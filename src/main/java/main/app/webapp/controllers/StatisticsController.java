package main.app.webapp.controllers;

import main.app.services.StatisticsService;
import main.app.webapp.DTO.StatisticsDtoWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsController {

    private final StatisticsService statisticsService;


    @Autowired
    public StatisticsController( StatisticsService statisticsService ) {
        this.statisticsService = statisticsService;

    }

    @GetMapping("/api/statistics")
    public ResponseEntity<StatisticsDtoWrapper> statistics() {
        StatisticsDtoWrapper statistics = statisticsService.getStatistics();
        if(statistics == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(statistics);
    }
}
