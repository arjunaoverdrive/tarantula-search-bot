package main.app.webapp.controllers;

import main.app.services.SiteService;
import main.app.webapp.DTO.ResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class SiteController {

    @Autowired
    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
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
    public ResponseEntity indexPage(@RequestParam String url) throws Exception {
        ResultDto result = siteService.indexPage(url);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
