package org.arjunaoverdrive.app.services;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.FieldRepository;
import org.arjunaoverdrive.app.DAO.LemmaRepository;
import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.model.Site;
import org.arjunaoverdrive.app.model.StatusEnum;
import org.arjunaoverdrive.app.pagevisitor.WebPageVisitorStarter;
import org.arjunaoverdrive.app.webapp.DTO.ResultDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

@Service
public class AllSitesService extends SiteService{

    private static final Logger LOGGER = Logger.getLogger(AllSitesService.class);

    public AllSitesService(FieldRepository fieldRepository,
                           SiteRepository siteRepository,
                           LemmaRepository lemmaRepository,
                           PageRepository pageRepository,
                           JdbcTemplate jdbcTemplate,
                           ConfigProperties props,
                           AppState appState) {
        super(fieldRepository, siteRepository, lemmaRepository, pageRepository, jdbcTemplate, props, appState);
    }

    public void saveSites() {
        Set<ConfigProperties.Site> sitesFromConfig = readSitesFromConfig();
        compareSitesInDbAndConfig(sitesFromConfig);

        LOGGER.info("Reading sites from config");
        for (ConfigProperties.Site s : sitesFromConfig) {
            if (appState.isStopped()) {                   //not to drop data for all sites if indexing is interrupted
                break;
            }
            Site site = getSite(s);

            WebPageVisitorStarter recursiveActionStarter = getWebPageVisitorThread(site);
            Thread t = new Thread(recursiveActionStarter);
            t.start();
            try {
                t.join();
            } catch (Exception e) {
                LOGGER.error(e);
            }
        }
    }

    private Site getSite(ConfigProperties.Site s) {
        Site site = getSiteByName(s.getName());
        return site == null ? saveSiteBeforeIndexing(s) : removeSiteDataFromDb(site);
    }

    private Site saveSiteBeforeIndexing(ConfigProperties.Site s){
        return siteRepository.save(new Site(LocalDateTime.now(), "",
                s.getUrl(), s.getName(), StatusEnum.INDEXING));
    }

    public ResultDto startReindexing() throws ExecutionException, InterruptedException {
        if (appState.isIndexing()) {
            return new ResultDto.Error("Indexing is already in progress");
        }
        appState.setStopped(false);

        List<Thread> taskThreads = new LinkedList<>();
        FutureTask<ResultDto> task = getResultDtoFutureTask(taskThreads);
        Thread mainThread = new Thread(this::saveSites);
        taskThreads.add(mainThread);

        taskThreads.forEach(Thread::start);
        return task.get();
    }

    private FutureTask<ResultDto> getResultDtoFutureTask(List<Thread> threads) { //service task that sends ResultDto on indexing startup
        Callable<ResultDto> statusCheck = ResultDto.Success::new;
        FutureTask<ResultDto> task = new FutureTask<>(statusCheck);
        Thread statusCheckThread = new Thread(task);
        threads.add(statusCheckThread);
        return task;
    }

    public ResultDto stopIndexing() {
        if (!appState.isIndexing() || appState.isStopped()) {
            return new ResultDto.Error("Indexing is not in progress");
        }
        appState.setStopped(true);
        return new ResultDto.Success();
    }

    private Site getSiteByName(String siteName) {
        Site site = siteRepository.findByName(siteName);
        return site;
    }


    private void compareSitesInDbAndConfig(Set<ConfigProperties.Site> siteFromConfig) {
        List<Site> sitesFromDb = siteRepository.findAll();
        List<String> siteFromConfigNames = siteFromConfig.stream().map(ConfigProperties.Site::getName)
                .collect(Collectors.toList());
        for (Site s : sitesFromDb) {
            if (!siteFromConfigNames.contains(s.getName())) {
                removeSiteDataFromDb(s);
                siteRepository.delete(s);
            }
        }
    }

    private Set<ConfigProperties.Site> readSitesFromConfig() {
        return props.getSites();
    }
}
