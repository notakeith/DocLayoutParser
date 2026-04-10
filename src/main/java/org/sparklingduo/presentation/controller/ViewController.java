package org.sparklingduo.presentation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/templates")
public class ViewController {

    @GetMapping // Главная страница со списком
    public String list() {
        return "template-list";
    }

    @GetMapping("/editor")
    public String editor() {
        return "template-editor";
    }

    @GetMapping("/recognize/{id}")
    public String recognizePage(@PathVariable UUID id, Model model) {
        model.addAttribute("templateId", id);
        return "recognize";
    }
}