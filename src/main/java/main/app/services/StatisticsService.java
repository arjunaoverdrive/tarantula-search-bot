package main.app.services;

import main.app.DAO.LemmaRepository;
import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.model.Site;
import main.app.webapp.DTO.SiteDto;
import main.app.webapp.DTO.StatisticsDto;
import main.app.webapp.DTO.StatisticsDtoWrapper;
import main.app.webapp.DTO.TotalStatistics;
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
            siteService.saveSites();
            LOGGER.info("Initial indexing. Sites count " + sites.size());
        }
        StatisticsDto stats = new StatisticsDto(total, sites);
        return new StatisticsDtoWrapper(true, stats);
    }

    public TotalStatistics getTotalStatistics() {
        List<Site> sites = siteRepository.findAll();
        int sitesCount = sites.size();
        int pages = pageRepository.findAll().size();
        int lemmas = lemmaRepository.findAll().size();
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
                dto.setStatus(s.getStatus().toString());
                dto.setStatusTime(s.getStatusTime());
                dto.setPages(pageRepository.countBySiteId(s.getId()));
                dto.setLemmas(lemmaRepository.countBySiteId(s.getId()));
                dto.setError(s.getLastError() != null ? s.getLastError() : "");
                dtos.add(dto);
            }
        }
        return dtos;
    }
}
