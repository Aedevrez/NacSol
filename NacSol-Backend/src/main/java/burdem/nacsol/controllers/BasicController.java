package burdem.nacsol.controllers;

import burdem.nacsol.services.BasicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BasicController {
    @Autowired
    BasicService basicService;

    @GetMapping("/")
    public String index() {
        return basicService.index();
    }
}
