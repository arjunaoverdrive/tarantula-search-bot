package main.app.DAO;

import main.app.model.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    int countBySiteId(int id);
}
