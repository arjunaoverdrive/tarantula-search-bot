package org.arjunaoverdrive.app.DAO;

import org.arjunaoverdrive.app.model.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    int countBySiteId(int id);
    List<Page>findBySiteId(int siteId);

    Page findByPath(String path);
}
