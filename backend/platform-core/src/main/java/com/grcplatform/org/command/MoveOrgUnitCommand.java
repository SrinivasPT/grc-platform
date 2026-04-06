package com.grcplatform.org.command;

import java.util.List;
import java.util.UUID;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.validation.Validator;
import com.grcplatform.org.OrgUnitDto;
import com.grcplatform.org.OrgUnitRepository;

public record MoveOrgUnitCommand(UUID unitId, UUID newParentId) {
}

