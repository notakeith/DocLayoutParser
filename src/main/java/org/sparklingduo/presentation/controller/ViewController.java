package org.sparklingduo.presentation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/templates")
public class ViewController {

    @GetMapping("/editor")
    public String editor() {
        return "template-editor"; // Имя файла в src/main/resources/templates/
    }
}