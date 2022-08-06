package org.arjunaoverdrive.app.services;

import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.model.Page;
import org.arjunaoverdrive.app.model.Site;
import org.arjunaoverdrive.app.webapp.DTO.ErrorDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ErrorService {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    @Autowired
    public ErrorService(PageRepository pageRepository, SiteRepository siteRepository) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    public ErrorDto getErrorDto(){
        return new ErrorDto(listErrors());
    }
    private List<ErrorDto.ErrorPage> listErrors() {
        List<Page> errorPages = pageRepository.findByCodeNot(200);
        List<ErrorDto.ErrorPage> errors = new ArrayList<>();
        Map<Integer, String> idToUrl = getIdToSiteUrl();
        errorPages.stream().map(p -> errors.add(new ErrorDto.ErrorPage(p.getPath(), idToUrl.get(p.getId()))));
        return errors;
    }

    private Map<Integer, String> getIdToSiteUrl(){
        List<Site> sites = siteRepository.findAll();
        Map<Integer, String> idToUrl = new HashMap<>();
        sites.forEach(s -> idToUrl.put(s.getId(), s.getUrl()));
        return idToUrl;
    }
}
