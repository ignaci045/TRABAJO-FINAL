package com.example.facturaventa.service;
import com.example.facturaventa.dto.*;
import com.example.facturaventa.model.*;
import com.example.facturaventa.exception.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @Transactional(readOnly=true)
public class ExploradorService {
 @PersistenceContext private EntityManager em;
 public record ArticuloFila(Long articuloId,String descripcion,String codigo,String rubro,String marca,
  java.math.BigDecimal alicuotaIva,List<CatalogosDTO.PrecioArticuloOpcionDTO> precios){}
 public record PaginaArticulos(List<ArticuloFila> content,int number,int size,long totalElements,int totalPages,long totalPrecios){}
 public static void validar(int page,int size){if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new ReglaNegocioException("Paginación inválida");}
 public PaginaArticulos buscar(String texto,Long listaId,int page,int size){
  validar(page,size);
  String desde=" FROM ListaPrecioArticulo p JOIN p.articulo a JOIN p.listaPrecio l LEFT JOIN a.rubro r LEFT JOIN a.marca m WHERE p.activo=true AND a.activo=true AND l.activo=true";
  Map<String,Object> params=new HashMap<>();
  if(listaId!=null){desde+=" AND l.id=:lista";params.put("lista",listaId);}
  if(texto!=null&&!texto.isBlank()){
   desde+=" AND (lower(a.descripcion) LIKE :q OR lower(coalesce(a.codigo,'')) LIKE :q OR lower(coalesce(r.denominacion,'')) LIKE :q OR lower(coalesce(m.denominacion,'')) LIKE :q OR lower(l.denominacion) LIKE :q OR cast(p.id AS string)=:identificador)";
   params.put("q","%"+texto.trim().toLowerCase(Locale.ROOT)+"%");params.put("identificador",texto.trim());
  }
  var count=em.createQuery("SELECT count(DISTINCT a.id)"+desde,Long.class);params.forEach(count::setParameter);long total=count.getSingleResult();
  var countPrecios=em.createQuery("SELECT count(p.id)"+desde,Long.class);params.forEach(countPrecios::setParameter);long totalPrecios=countPrecios.getSingleResult();
  var query=em.createQuery("SELECT DISTINCT a"+desde+" ORDER BY a.descripcion,a.id",Articulo.class);params.forEach(query::setParameter);
  var articulos=query.setFirstResult(page*size).setMaxResults(size).getResultList();
  List<ListaPrecioArticulo> precios=List.of();
  if(!articulos.isEmpty()){
   var qp=em.createQuery("SELECT p"+desde+" AND a.id IN :ids ORDER BY a.descripcion,l.denominacion,p.id",ListaPrecioArticulo.class);
   params.forEach(qp::setParameter);qp.setParameter("ids",articulos.stream().map(Articulo::getId).toList());precios=qp.getResultList();
  }
  List<ArticuloFila> filas=new ArrayList<>();
  for(var a:articulos){List<CatalogosDTO.PrecioArticuloOpcionDTO> opciones=precios.stream().filter(p->p.getArticulo().getId().equals(a.getId())).map(ExploradorService::opcion).toList();filas.add(new ArticuloFila(a.getId(),a.getDescripcion(),a.getCodigo(),a.getRubro()==null?null:a.getRubro().getDenominacion(),a.getMarca()==null?null:a.getMarca().getDenominacion(),a.getAlicuotaIva(),opciones));}
  return new PaginaArticulos(filas,page,size,total,(int)Math.ceil((double)total/size),totalPrecios);
 }
 public CatalogosDTO.PrecioArticuloOpcionDTO precio(Long id){return opcion(em.find(ListaPrecioArticulo.class,id));}
 private static CatalogosDTO.PrecioArticuloOpcionDTO opcion(ListaPrecioArticulo p){
  if(p==null)throw new RecursoNoEncontradoException("Precio no encontrado");
  var a=p.getArticulo();return new CatalogosDTO.PrecioArticuloOpcionDTO(p.getId(),a.getId(),a.getDescripcion(),p.getListaPrecio().getId(),p.getListaPrecio().getDenominacion(),p.getPrecioNeto(),a.getAlicuotaIva(),a.getCodigo(),a.getRubro()==null?null:a.getRubro().getDenominacion(),a.getMarca()==null?null:a.getMarca().getDenominacion());
 }
}
