package main.app.webapp.controllers;

import main.app.services.SiteService;
import main.app.webapp.DTO.ResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteController {

    @Autowired
    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping(value = "/api/startIndexing")
    public ResponseEntity startIndexing(){
        ResultDto result =
            siteService.startReindexing();
        if(result.isResult()) {
            return ResponseEntity.status(HttpStatus.OK).body(result);
        } else {
            ResultDto.Error error = (ResultDto.Error)result;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping(value = "/api/stopIndexing")
    public ResponseEntity stopIndexing(){
        ResultDto result = siteService.stopIndexing();
        if(result.isResult()) {
            return ResponseEntity.status(HttpStatus.OK).body(result);
        } else {
            ResultDto.Error error = (ResultDto.Error)result;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping(value = "/api/indexPage")
    public ResponseEntity indexPage(@RequestParam String url){
        if(url.isEmpty()){
            ResultDto.Error error = new ResultDto.Error("Задана пустая страница");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        ResultDto result = siteService.indexPage(url);
        if(result.isResult()) {
            return ResponseEntity.status(HttpStatus.OK).body(result);
        } else {
            ResultDto.Error error = (ResultDto.Error) result;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
