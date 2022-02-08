package main.app.webapp.controllers;

import main.app.services.SearchService;
import main.app.webapp.DTO.SearchDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
        SearchDto result = null;
        if(query.isEmpty()){
            SearchDto.Error error = new SearchDto.Error("Задан пустой поисковый запрос");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        try {
            result = searchService.doSearch(query, site, offset, limit);
            if(result.isResult()) {
                return ResponseEntity.status(HttpStatus.OK).body(result);
            } SearchDto.Error error = (SearchDto.Error) result;
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e);
        }
    }

}
