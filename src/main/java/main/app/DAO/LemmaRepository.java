package main.app.DAO;

import main.app.model.Lemma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    int countBySiteId(int id);
}
