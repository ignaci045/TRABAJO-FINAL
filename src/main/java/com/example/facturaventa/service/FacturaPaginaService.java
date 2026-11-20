package com.example.facturaventa.service;
import com.example.facturaventa.dto.FacturaGestionDTO;
import com.example.facturaventa.model.Cliente;
import com.example.facturaventa.exception.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@Service @Transactional(readOnly=true)
public class FacturaPaginaService {
 @PersistenceContext private EntityManager em;
 public record PaginaFacturas(List<FacturaGestionDTO> content,int number,int size,long totalElements,int totalPages,double importeTotal,long cantidadAnuladas){}
 public PaginaFacturas buscar(LocalDate desde,LocalDate hasta,String estado,Double minimo,String numero,String cliente,Long clienteId,int page,int size){
  ExploradorService.validar(page,size);
  if(desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("La fecha desde debe ser anterior o igual a fecha hasta");
  if(minimo!=null&&(!Double.isFinite(minimo)||minimo<0))throw new ReglaNegocioException("Monto mínimo inválido");
  if(clienteId!=null&&em.find(Cliente.class,clienteId)==null)throw new RecursoNoEncontradoException("Cliente no encontrado");
  String base=" FROM FacturaVenta f LEFT JOIN f.cliente c JOIN f.condicionIva ci JOIN f.puntoVenta pv";
  String where=" WHERE 1=1";Map<String,Object> p=new HashMap<>();
  if(desde!=null){where+=" AND f.fechaEmision>=:desde";p.put("desde",java.sql.Date.valueOf(desde));}
  if(hasta!=null){where+=" AND f.fechaEmision<=:hasta";p.put("hasta",java.sql.Date.valueOf(hasta));}
  if(estado!=null&&!estado.isBlank()){String e=estado.trim().toUpperCase(Locale.ROOT);if(!Set.of("EMITIDA","ANULADA").contains(e))throw new ReglaNegocioException("Estado inválido");where+=" AND f.estado=:estado";p.put("estado",e);}
  if(minimo!=null){where+=" AND f.importeTotal>=:minimo";p.put("minimo",minimo);}
  if(clienteId!=null){where+=" AND c.id=:clienteId";p.put("clienteId",clienteId);}
  if(cliente!=null&&!cliente.isBlank()){where+=" AND lower(coalesce(c.denominacion,'Consumidor Final')) LIKE :cliente";p.put("cliente","%"+cliente.trim().toLowerCase(Locale.ROOT)+"%");}
  if(numero!=null&&!numero.isBlank()){
   String n=numero.trim().replaceFirst("^#","");
   try{if(n.matches("\\d+")){where+=" AND (f.numero=:numero OR f.numeroFiscal=:numero)";p.put("numero",Long.valueOf(n));}
   else if(n.matches("\\d+-\\d+")){var partes=n.split("-");where+=" AND f.puntoVentaFiscal=:punto AND f.numeroFiscal=:fiscal";p.put("punto",Integer.valueOf(partes[0]));p.put("fiscal",Long.valueOf(partes[1]));}
   else where+=" AND f.id=-1";}catch(NumberFormatException e){throw new ReglaNegocioException("Número de comprobante inválido");}
  }
  var qc=em.createQuery("SELECT count(f), coalesce(sum(f.importeTotal),0), coalesce(sum(case when f.estado='ANULADA' then 1 else 0 end),0)"+base+where,Object[].class);p.forEach(qc::setParameter);var resumen=qc.getSingleResult();
  long total=((Number)resumen[0]).longValue();
  String select="SELECT new com.example.facturaventa.dto.FacturaGestionDTO(f.id,f.estado,f.numero,f.fechaEmision,coalesce(c.denominacion,'Consumidor Final'),ci.denominacion,pv.descripcion,f.importeTotal,count(d),f.cae,f.puntoVentaFiscal,f.numeroFiscal)";
  String grupo=" GROUP BY f.id,f.estado,f.numero,f.fechaEmision,c.denominacion,ci.denominacion,pv.descripcion,f.importeTotal,f.cae,f.puntoVentaFiscal,f.numeroFiscal ORDER BY f.fechaEmision DESC,f.id DESC";
  var query=em.createQuery(select+base+" LEFT JOIN f.detalles d"+where+grupo,FacturaGestionDTO.class);p.forEach(query::setParameter);
  return new PaginaFacturas(query.setFirstResult(page*size).setMaxResults(size).getResultList(),page,size,total,(int)Math.ceil((double)total/size),((Number)resumen[1]).doubleValue(),((Number)resumen[2]).longValue());
 }
}
