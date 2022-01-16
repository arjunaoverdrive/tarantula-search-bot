package main.app.services;

import main.app.DAO.IndexRepository;
import main.app.DAO.LemmaRepository;
import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.config.ConfigProperties;
import main.app.model.Site;
import main.app.model.StatusEnum;
import main.app.pagevisitor.WebPageVisitorStarter;
import main.app.webapp.DTO.ResultDto;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final ConfigProperties props;
    private final AppState appState;
    private Set<WebPageVisitorStarter> starters = new HashSet<>();

    private static final Logger LOGGER = Logger.getLogger(SiteService.class);

    @Autowired
    public SiteService(SiteRepository siteRepository,
                       LemmaRepository lemmaRepository,
                       PageRepository pageRepository,
                       IndexRepository indexRepository,
                       ConfigProperties props,
                       AppState appState) {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.props = props;
        this.appState = appState;
    }

    private Set<ConfigProperties.Site> readSitesFromConfig() {
        return props.getSites();
    }

    public void saveSites() {

        LOGGER.info("Starting saving sites");
        for (ConfigProperties.Site s : readSitesFromConfig()) {
            Site site = siteRepository.findByName(s.getName());
            if (site == null) {
                WebPageVisitorStarter recursiveActionStarter = getWebPageVisitorThread(s);
                starters.add(recursiveActionStarter);
            }
        }

        for (WebPageVisitorStarter starter : starters) {
            Thread t = new Thread(starter);
            t.start();
        }
        LOGGER.info("Starters size : " + starters.size());
        starters = new HashSet<>();

    }

    private WebPageVisitorStarter getWebPageVisitorThread(ConfigProperties.Site site2save) {
        Site site = new Site(LocalDateTime.now(), "", site2save.getUrl(), site2save.getName(), StatusEnum.INDEXING);
        return new WebPageVisitorStarter(site, lemmaRepository, pageRepository, siteRepository, props, indexRepository, appState);
    }

    private void clearDbB4reindexing() {
        LOGGER.info("Starting truncating index table");
        indexRepository.deleteAllInBatch();
        LOGGER.info("Truncated index table");
        lemmaRepository.deleteAllInBatch();
        LOGGER.info("Truncated lemma table");
        pageRepository.deleteAllInBatch();
        LOGGER.info("Truncated page table");
        siteRepository.deleteAllInBatch();
        LOGGER.info("Truncated site table. \nFinished truncating tables");

    }

    public ResultDto startReindexing() {
        if (appState.isIndexing()) {
            System.out.println("Starters size : " + starters.size());
            return new ResultDto.Error("Indexing is already in progress");
        } else {
            appState.setStopped(false);
            clearDbB4reindexing();
            saveSites();
            return new ResultDto.Success();
        }
    }

    public ResultDto stopIndexing() {
        if (!appState.isIndexing() || appState.isStopped()) {
            return new ResultDto.Error("Indexing is not in progress");
        } else {
            appState.setStopped(true);
            starters.forEach(starter -> starter.getThread().interrupt());
            starters = new HashSet<>();
            return new ResultDto.Success();
        }
    }
}
