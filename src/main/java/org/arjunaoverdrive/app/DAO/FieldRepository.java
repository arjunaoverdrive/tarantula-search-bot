package org.arjunaoverdrive.app.DAO;

import org.arjunaoverdrive.app.model.Field;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldRepository extends JpaRepository<Field, Integer> {
}
