package com.grcplatform.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.org.command.CreateOrgUnitHandler;
import com.grcplatform.org.command.MoveOrgUnitHandler;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgHierarchyServiceTest {

        @Mock
        OrgUnitRepository orgUnitRepository;
        @Mock
        UserOrgUnitRepository userOrgUnitRepository;

        private OrgHierarchyServiceImpl service;

        private static final UUID ORG_ID = UUID.randomUUID();
        private static final UUID USER_ID = UUID.randomUUID();

        @BeforeEach
        void setUp() {
                var createHandler = new CreateOrgUnitHandler(orgUnitRepository, List.of());
                var moveHandler = new MoveOrgUnitHandler(orgUnitRepository, List.of());
                service = new OrgHierarchyServiceImpl(orgUnitRepository, userOrgUnitRepository,
                                createHandler, moveHandler);
        }

        private void withContext(Runnable action) {
                var ctx = new SessionContext(ORG_ID, USER_ID, "analyst", List.of("analyst"), 1);
                ScopedValue.where(SessionContextHolder.SESSION, ctx).run(action);
        }

        // ─── createUnit ──────────────────────────────────────────────────────────

        @Test
        void createUnit_rootUnit_hasDepthZeroAndSlashPath() {
                var cmd = new CreateOrgUnitCommand("Risk Division", "division", "DIV-001",
                                "Manages risk", null, null, 0);

                when(orgUnitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                withContext(() -> {
                        var dto = service.createUnit(cmd);
                        assertThat(dto.depth()).isZero();
                        assertThat(dto.path()).startsWith("/");
                        assertThat(dto.path()).endsWith("/");
                        assertThat(dto.parentId()).isNull();
                });

                var captor = ArgumentCaptor.forClass(OrganizationUnit.class);
                verify(orgUnitRepository).save(captor.capture());
                assertThat(captor.getValue().getOrgId()).isEqualTo(ORG_ID);
        }

        @Test
        void createUnit_withParent_inheritsDepthAndPath() {
                var parentId = UUID.randomUUID();
                var parentSegment = parentId.toString().replace("-", "");
                var parent = OrganizationUnit.create(ORG_ID, null, "/" + parentSegment + "/", 0,
                                "division", "DIV-001", "Division", null, null);
                parent.setId(parentId);

                when(orgUnitRepository.findByIdAndOrgId(eq(parentId), eq(ORG_ID)))
                                .thenReturn(Optional.of(parent));
                when(orgUnitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var cmd = new CreateOrgUnitCommand("Cyber Dept", "department", "DEPT-001", null,
                                parentId, null, 0);

                withContext(() -> {
                        var dto = service.createUnit(cmd);
                        assertThat(dto.depth()).isEqualTo(1);
                        assertThat(dto.path()).startsWith("/" + parentSegment + "/");
                        assertThat(dto.parentId()).isEqualTo(parentId);
                });
        }

        @Test
        void createUnit_withBlankName_throwsValidationException() {
                var cmd = new CreateOrgUnitCommand("   ", "division", null, null, null, null, 0);

                withContext(() -> assertThatThrownBy(() -> service.createUnit(cmd))
                                .isInstanceOf(ValidationException.class));
        }

        @Test
        void createUnit_withUnknownParent_throwsRecordNotFoundException() {
                var missingParentId = UUID.randomUUID();
                when(orgUnitRepository.findByIdAndOrgId(eq(missingParentId), eq(ORG_ID)))
                                .thenReturn(Optional.empty());

                var cmd = new CreateOrgUnitCommand("Child", "department", null, null,
                                missingParentId, null, 0);

                withContext(() -> assertThatThrownBy(() -> service.createUnit(cmd))
                                .isInstanceOf(RecordNotFoundException.class));
        }

        // ─── moveUnit ────────────────────────────────────────────────────────────

        @Test
        void moveUnit_preventsMovingUnitUnderOwnDescendant() {
                var parentId = UUID.randomUUID();
                var parentSeg = parentId.toString().replace("-", "");
                var childId = UUID.randomUUID();
                var childSeg = childId.toString().replace("-", "");

                var parent = OrganizationUnit.create(ORG_ID, null, "/" + parentSeg + "/", 0,
                                "division", "P", "Parent", null, null);
                parent.setId(parentId);
                var childUnit = OrganizationUnit.create(ORG_ID, parentId,
                                "/" + parentSeg + "/" + childSeg + "/", 1, "department", "C",
                                "Child", null, null);
                childUnit.setId(childId);

                when(orgUnitRepository.findByIdAndOrgId(eq(parentId), eq(ORG_ID)))
                                .thenReturn(Optional.of(parent));
                when(orgUnitRepository.findByIdAndOrgId(eq(childId), eq(ORG_ID)))
                                .thenReturn(Optional.of(childUnit));

                withContext(() -> assertThatThrownBy(
                                () -> service.moveUnit(new MoveOrgUnitCommand(parentId, childId)))
                                                .isInstanceOf(ValidationException.class)
                                                .hasMessageContaining("descendant"));
        }

        // ─── findDirectManagerId ─────────────────────────────────────────────────

        @Test
        void findDirectManagerId_returnsManagerOfPrimaryUnit() {
                var unitId = UUID.randomUUID();
                var managerId = UUID.randomUUID();
                var membership = UserOrgUnit.create(USER_ID, unitId, true);
                var unitSeg = unitId.toString().replace("-", "");
                var unit = OrganizationUnit.create(ORG_ID, null, "/" + unitSeg + "/", 0, "division",
                                null, "Div", null, managerId);
                unit.setId(unitId);

                when(userOrgUnitRepository.findPrimaryByUserId(USER_ID))
                                .thenReturn(Optional.of(membership));
                when(orgUnitRepository.findByIdAndOrgId(eq(unitId), eq(ORG_ID)))
                                .thenReturn(Optional.of(unit));

                withContext(() -> {
                        var result = service.findDirectManagerId(USER_ID);
                        assertThat(result).contains(managerId);
                });
        }

        @Test
        void findDirectManagerId_returnsEmpty_whenUserHasNoPrimaryUnit() {
                when(userOrgUnitRepository.findPrimaryByUserId(USER_ID))
                                .thenReturn(Optional.empty());

                withContext(() -> {
                        var result = service.findDirectManagerId(USER_ID);
                        assertThat(result).isEmpty();
                });
        }

        // ─── getSubtreeIds ───────────────────────────────────────────────────────

        @Test
        void getSubtreeIds_returnsAllIdsInSubtree() {
                var rootId = UUID.randomUUID();
                var rootSeg = rootId.toString().replace("-", "");
                var root = OrganizationUnit.create(ORG_ID, null, "/" + rootSeg + "/", 0, "division",
                                null, "Root", null, null);
                root.setId(rootId);

                var childId = UUID.randomUUID();
                var child = OrganizationUnit.create(ORG_ID, rootId,
                                "/" + rootSeg + "/" + childId.toString().replace("-", "") + "/", 1,
                                "department", null, "Child", null, null);
                child.setId(childId);

                when(orgUnitRepository.findByIdAndOrgId(eq(rootId), eq(ORG_ID)))
                                .thenReturn(Optional.of(root));
                when(orgUnitRepository.findByOrgIdAndPathStartingWith(eq(ORG_ID),
                                eq(root.getPath()))).thenReturn(List.of(root, child));

                withContext(() -> {
                        var ids = service.getSubtreeIds(rootId);
                        assertThat(ids).containsExactlyInAnyOrder(rootId, childId);
                });
        }
}
