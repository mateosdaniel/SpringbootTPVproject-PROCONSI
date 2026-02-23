package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.CategoryService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SaleService saleService;

    @GetMapping
    public String index(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            Model model) {

        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("selectedCategoryId", categoryId);

        List<Product> products;
        if (search != null && !search.isBlank()) {
            products = productService.search(search);
        } else if (categoryId != null) {
            products = productService.findByCategory(categoryId);
        } else {
            products = productService.findAllActiveWithCategory();
        }

        model.addAttribute("products", products);
        model.addAttribute("search", search);
        model.addAttribute("totalToday", saleService.sumTotalToday());
        model.addAttribute("countToday", saleService.countToday());

        return "tpv/index";
    }

    @PostMapping("/sale")
    public String processSale(
            @RequestParam List<Long> productIds,
            @RequestParam List<Integer> quantities,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        List<SaleLine> lines = java.util.stream.IntStream.range(0, productIds.size())
                .mapToObj(i -> {
                    Product product = productService.findById(productIds.get(i));
                    return SaleLine.builder()
                            .product(product)
                            .quantity(quantities.get(i))
                            .unitPrice(product.getPrice())
                            .build();
                }).collect(Collectors.toList());

        Sale sale = saleService.createSale(lines, paymentMethod, notes);

        redirectAttributes.addFlashAttribute("successMessage",
                "Venta #" + sale.getId() + " registrada. Total: " + sale.getTotalAmount() + "€");

        return "redirect:/tpv";
    }
}
