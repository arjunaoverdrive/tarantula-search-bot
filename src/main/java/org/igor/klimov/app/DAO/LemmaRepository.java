package org.igor.klimov.app.DAO;

import org.igor.klimov.app.model.Lemma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    int countBySiteId(int id);

    List<Lemma> findBySiteId(int id);

    List<Lemma> findAllBySiteIdAndLemmaIn(int siteId,Set<String> lemmas);
}
