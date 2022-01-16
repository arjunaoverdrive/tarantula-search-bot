package main.app.DAO;

import main.app.model.Index;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexRepository extends JpaRepository<Index, Integer> {
}
