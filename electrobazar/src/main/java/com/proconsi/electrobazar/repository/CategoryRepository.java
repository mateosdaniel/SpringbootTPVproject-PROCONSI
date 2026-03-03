package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Category> {

    // Para el formulario: comprobar si ya existe una categoría con ese nombre
    boolean existsByNameIgnoreCase(String name);

    // Solo categorías activas para el TPV
    List<Category> findByActiveTrueOrderByNameAsc();

    // Buscar por nombre (útil para buscador)
    Optional<Category> findByNameIgnoreCase(String name);
}