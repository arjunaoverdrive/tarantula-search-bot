package org.arjunaoverdrive.app.DAO;

import org.arjunaoverdrive.app.model.Site;
import org.arjunaoverdrive.app.model.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    Site findByName(String name);

    Site findByUrl(String targetSite);

    Site findByStatusEquals(StatusEnum indexing);

    List<Site> findAllByIdIn(List<Integer> siteIds);
}
