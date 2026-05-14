package com.plantilla.backend.modules.contingencia.repository;

import com.plantilla.backend.modules.contingencia.entity.EventoOperativo;
import com.plantilla.backend.shared.enums.TipoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de eventos operativos.
 * Principio SOLID (I): Interfaz segregada para persistencia de eventos.
 */
@Repository
public interface EventoOperativoRepository extends JpaRepository<EventoOperativo, Integer> {

    List<EventoOperativo> findByTipoEvento(TipoEvento tipoEvento);

    List<EventoOperativo> findByVueloIdVuelo(Integer idVuelo);

    List<EventoOperativo> findByAeropuertoIdAeropuerto(Integer idAeropuerto);
}
