package org.arjunaoverdrive.app.webapp.controllers;

import org.arjunaoverdrive.app.services.AllSitesService;
import org.arjunaoverdrive.app.services.IndexOnePageService;
import org.arjunaoverdrive.app.services.IndexOneSiteService;
import org.arjunaoverdrive.app.webapp.DTO.ResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@CrossOrigin(origins = "*")
public class SiteController {

    @Autowired
    private final AllSitesService siteService;
    private final IndexOneSiteService oneSiteService;
    private final IndexOnePageService onePageService;

    public SiteController(AllSitesService siteService, IndexOneSiteService oneSiteService, IndexOnePageService onePageService) {
        this.siteService = siteService;
        this.oneSiteService = oneSiteService;
        this.onePageService = onePageService;
    }

    @GetMapping(value = "/api/startIndexing")
    public ResponseEntity startIndexing() throws InterruptedException, ExecutionException {
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
