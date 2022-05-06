package org.igor.klimov.app.DAO;

import org.igor.klimov.app.model.Field;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldRepository extends JpaRepository<Field, Integer> {
}
