package org.igor.klimov.app.services;

import org.igor.klimov.app.DAO.LemmaRepository;
import org.igor.klimov.app.DAO.PageRepository;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.model.Site;
import org.igor.klimov.app.webapp.DTO.SiteDto;
import org.igor.klimov.app.webapp.DTO.StatisticsDto;
import org.igor.klimov.app.webapp.DTO.StatisticsDtoWrapper;
import org.igor.klimov.app.webapp.DTO.TotalStatistics;
import org.igor.klimov.app.model.StatusEnum;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final AppState appState;
    private final SiteService siteService;

    private final static Logger LOGGER = Logger.getLogger(StatisticsService.class);

    @Autowired
    public StatisticsService(SiteRepository siteRepository,
                             LemmaRepository lemmaRepository,
                             PageRepository pageRepository,
                             AppState appState, SiteService siteService) {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.appState = appState;
        this.siteService = siteService;
    }

    public StatisticsDtoWrapper getStatistics() {
        TotalStatistics total = getTotalStatistics();
        List<SiteDto> sites = getSiteDtos() == null ? new ArrayList<>() : getSiteDtos();
        if(sites.size() == 0){
            LOGGER.info("Initial indexing. Sites count " + sites.size());
            siteService.saveSites();
        }
        StatisticsDto stats = new StatisticsDto(total, sites);
        return new StatisticsDtoWrapper(true, stats);
    }

    public TotalStatistics getTotalStatistics() {
        int sitesCount = (int) siteRepository.count();
        int pages = (int) pageRepository.count();
        int lemmas = (int) lemmaRepository.count();
        boolean isIndexing = appState.isIndexing();
        return new TotalStatistics(sitesCount, pages, lemmas, isIndexing);
    }

    public List<SiteDto> getSiteDtos() {

        List<SiteDto> dtos = new ArrayList<>();
        List<Site> sites = siteRepository.findAll();
        if (sites.size() != 0) {
            for (Site s : sites) {
                SiteDto dto = new SiteDto();
                dto.setUrl(s.getUrl());
                dto.setName(s.getName());
                dto.setStatusTime(s.getStatusTime());
                dto.setPages(pageRepository.countBySiteId(s.getId()));
                dto.setLemmas(lemmaRepository.countBySiteId(s.getId()));
                dto.setError(s.getLastError() != null ? s.getLastError() : "");
                dto.setStatus(s.getStatus().toString());
                if(!appState.isIndexing() && s.getStatus().equals(StatusEnum.INDEXING)){
                    handleUnindexedSite(dto, s);
                    dto.setStatusTime(s.getStatusTime());
                }
                dtos.add(dto);
            }
        }
        return dtos;
    }

    private void handleUnindexedSite(SiteDto dto, Site s){
        s.setStatus(StatusEnum.FAILED);
        s.setLastError("Приложение было остановлено во время индексации");
        dto.setStatus(StatusEnum.FAILED.toString());
        dto.setError("Приложение было остановлено во время индексации");
        siteRepository.save(s);
    }
}
