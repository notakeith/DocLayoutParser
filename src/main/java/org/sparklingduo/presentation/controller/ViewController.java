package org.sparklingduo.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.sparklingduo.repository.TemplateRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ViewController {

    private final TemplateRepository templateRepository;

    @RequestMapping("/templates")
    public String list() {
        return "template-list";
    }

    @GetMapping("/templates/editor")
    public String editor() {
        return "template-editor";
    }

    @GetMapping("/templates/recognize/{id}")
    public String recognizePage(@PathVariable UUID id, Model model) {
        model.addAttribute("templateId", id);
        int pageCount = templateRepository.findById(id)
                .map(t -> t.getPageCount())
                .orElse(1);
        model.addAttribute("pageCount", pageCount);
        return "recognize";
    }

    @GetMapping("/jobs")
    public String jobsHistory() {
        return "jobs";
    }
}