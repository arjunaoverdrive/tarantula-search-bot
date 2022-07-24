package org.igor.klimov.app.webapp.controllers;

import org.igor.klimov.app.services.IndexOnePageService;
import org.igor.klimov.app.services.IndexOneSiteService;
import org.igor.klimov.app.services.SiteService;
import org.igor.klimov.app.webapp.DTO.ResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class SiteController {

    @Autowired
    private final SiteService siteService;
    private final IndexOneSiteService oneSiteService;
    private final IndexOnePageService onePageService;

    public SiteController(SiteService siteService, IndexOneSiteService oneSiteService, IndexOnePageService onePageService) {
        this.siteService = siteService;
        this.oneSiteService = oneSiteService;
        this.onePageService = onePageService;
    }

    @GetMapping(value = "/api/startIndexing")
    public ResponseEntity startIndexing() {
        ResultDto result = siteService.startReindexing();
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @GetMapping(value = "/api/stopIndexing")
    public ResponseEntity stopIndexing() {
        ResultDto result = siteService.stopIndexing();
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping(value = "/api/indexPage")
    public ResponseEntity indexPage(@RequestParam(name = "url") String url) throws Exception {
        ResultDto result = onePageService.indexPage(url);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping(value = "/api/indexSite")
    public ResponseEntity indexSite(@RequestParam(name= "url") String url) {
        ResultDto result = oneSiteService.indexSite(url);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
