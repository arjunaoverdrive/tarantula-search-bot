package org.igor.klimov.app.webapp.controllers;

import org.igor.klimov.app.services.SearchService;
import org.igor.klimov.app.webapp.DTO.SearchDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class SearchController {
    @Autowired
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public ResponseEntity search(@RequestParam String query,
                                 @RequestParam(required = false) String site,
                                 @RequestParam(required = false) int offset,
                                 @RequestParam(required = false) int limit) {
        SearchDto result = searchService.doSearch(query, site, offset, limit);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

}
