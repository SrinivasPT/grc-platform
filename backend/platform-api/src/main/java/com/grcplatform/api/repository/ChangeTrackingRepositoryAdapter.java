package com.grcplatform.api.repository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.grcplatform.graph.ChangeTrackingRepository;
import com.grcplatform.graph.model.TrackedChange;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Reads change tracking data from SQL Server using the CHANGETABLE function. Two tables are
 * tracked: records and record_relations.
 */
@Repository
public class ChangeTrackingRepositoryAdapter implements ChangeTrackingRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public long getCurrentVersion() {
        Number version = (Number) em.createNativeQuery("SELECT CHANGE_TRACKING_CURRENT_VERSION()")
                .getSingleResult();
        return version == null ? 0L : version.longValue();
    }

    @Override
    public List<TrackedChange> getChangesSince(long sinceVersion) {
        List<TrackedChange> changes = new ArrayList<>();
        changes.addAll(fetchChanges("records", sinceVersion));
        changes.addAll(fetchChanges("record_relations", sinceVersion));
        return changes;
    }

    @SuppressWarnings("unchecked")
    private List<TrackedChange> fetchChanges(String tableName, long sinceVersion) {
        String sql = """
                SELECT CT.SYS_CHANGE_OPERATION, CT.id
                FROM CHANGETABLE(CHANGES %s, :version) AS CT
                """.formatted(tableName);

        List<Object[]> rows =
                em.createNativeQuery(sql).setParameter("version", sinceVersion).getResultList();

        return rows.stream().map(row -> new TrackedChange(tableName, mapOperation((String) row[0]),
                row[1] == null ? null : row[1].toString(), null)).toList();
    }

    private static String mapOperation(String ctOp) {
        return switch (ctOp) {
            case "I" -> "INSERT";
            case "U" -> "UPDATE";
            case "D" -> "DELETE";
            default -> ctOp;
        };
    }
}
