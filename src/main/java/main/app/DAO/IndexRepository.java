package main.app.DAO;

import main.app.model.Index;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPageIdIn(List<Integer> pageIds);

    List<Index> findByPageId(int id);
}
