package com.example.facturaventa.arca;
import com.example.facturaventa.model.*;
import com.example.facturaventa.repository.FacturaVentaRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArcaConsumidorFinalTests {
 private org.w3c.dom.Document xml(String contenido)throws Exception{
  var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
  return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(contenido.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
 private FacturaVenta factura(boolean cliente){
  var f=new FacturaVenta();f.setId(1L);f.setFechaEmision(java.sql.Date.valueOf("2026-11-20"));f.setTipoMoneda(new TipoMoneda("ARS","Pesos"));f.setImporteTotal(100d);
  if(cliente)f.setCliente(new Cliente("Cliente registrado","40123456"));
  var d=new FacturaVentaDetalle();d.setAlicuotaIva(BigDecimal.ZERO);d.setImporteNeto(new BigDecimal("100.00"));d.setImporteIva(new BigDecimal("0.00"));f.agregarDetalle(d);return f;
 }
 @Test void facturaCSinCuentaMantieneImportesDocumentoYCae()throws Exception{
  var soap=mock(ArcaSoapClient.class);var repo=mock(FacturaVentaRepository.class);var f=factura(false);when(repo.findById(1L)).thenReturn(Optional.of(f));
  when(soap.invoke(eq("FECompUltimoAutorizado"),anyString())).thenReturn(xml("<r><CbteNro>10</CbteNro></r>"));
  when(soap.invoke(eq("FECAESolicitar"),anyString())).thenReturn(xml("<r><Resultado>A</Resultado><CAE>12345678901234</CAE><CAEFchVto>20261130</CAEFchVto></r>"));
  var servicio=new ArcaService(soap,new ArcaConfig(true,"homologacion","20123456789","test.p12","solo-test","test"),repo);
  var resultado=servicio.emitir(1L,new ArcaSolicitudDTO(3,11,99,0L,5));
  assertEquals(11L,resultado.numeroFiscal());assertEquals("12345678901234",f.getCae());assertNull(f.getCliente());
  verify(soap).invoke(eq("FECAESolicitar"),argThat(s->s.contains("<DocTipo>99</DocTipo><DocNro>0</DocNro>")&&s.contains("<CondicionIVAReceptorId>5</CondicionIVAReceptorId>")&&s.contains("<ImpTotal>100.00</ImpTotal>")&&!s.contains("<Iva>")));
  assertThrows(IllegalStateException.class,()->servicio.emitir(1L,new ArcaSolicitudDTO(3,11,99,0L,5)));
 }
 @Test void clienteRegistradoConservaFlujoAnterior()throws Exception{
  var soap=mock(ArcaSoapClient.class);var repo=mock(FacturaVentaRepository.class);var f=factura(true);when(repo.findById(1L)).thenReturn(Optional.of(f));
  when(soap.invoke(eq("FECompUltimoAutorizado"),anyString())).thenReturn(xml("<r><CbteNro>20</CbteNro></r>"));
  when(soap.invoke(eq("FECAESolicitar"),anyString())).thenReturn(xml("<r><Resultado>A</Resultado><CAE>12345678901235</CAE><CAEFchVto>20261130</CAEFchVto></r>"));
  var s=new ArcaService(soap,new ArcaConfig(true,"homologacion","20123456789","test.p12","solo-test","test"),repo);
  assertEquals(21L,s.emitir(1L,new ArcaSolicitudDTO(3,11,96,40123456L,5)).numeroFiscal());assertNotNull(f.getCliente());
 }
 @Test void sinCuentaRechazaCondicionIncompatibleAntesDeEnviar()throws Exception{
  var soap=mock(ArcaSoapClient.class);var repo=mock(FacturaVentaRepository.class);when(repo.findById(1L)).thenReturn(Optional.of(factura(false)));
  var s=new ArcaService(soap,new ArcaConfig(true,"homologacion","20123456789","test.p12","solo-test","test"),repo);
  assertThrows(IllegalArgumentException.class,()->s.emitir(1L,new ArcaSolicitudDTO(3,11,99,0L,1)));
  assertThrows(IllegalArgumentException.class,()->s.emitir(1L,new ArcaSolicitudDTO(3,1,99,0L,5)));
  verifyNoInteractions(soap);
 }
}
