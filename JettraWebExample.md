# JettraWebExample

## Backlog
### Modulo llamado Facturas
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T14:59:22.742184496" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
Crear un modulo llamado Facturas donde se tengan los entity, models, crudview
que se necesitan para un manejo de facturas. Productos, Clientes, Inventario, Ventas , Compras
Cuentas por pagar, cuentas por pagar, reportes, abonos. Crear los repository, controller(endpoint)

### Reoarganizar Model/Entity/Services
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T15:33:14.209579868" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
Analizar el uso de Entity a Model y Viceversa, puede ser una clase conversor.


## To Do
### Estudia JettraRest para ver la forma en que crea servicios Rest
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T15:28:14.191809381" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
Estudia JettraRest para ver la forma en que crea servicios Rest, y genera los clientes
Rest.
Luego diseña un modulo para la gestion de una biblioteca tienen autores, libros, editoriales, lectores
cada uno debe tener sus formularios para administracion. Los componentes que 
debes generar seran entity(java record ) correspondientes a cada entidad, luego
model(Modelos que permitiran integrarse a los formularios tenga en cuenta que
puede usar las anotaciones @SelectOne, @SelectMany y @ViewDataTable para generar 
maestro detalles.
Debe crear las paginas en el paquete .page.library
Debe crear los entitys dentro del paquete .entity.library
Debe crear los models dentro del paquete .model.library
Debe crear los controller dentro del paquete .controller.library
Debe crear los clientes dentro del paquete .restclient.library
Debe crear los repository dentro del paquete .repository.library
Recuerde crear los atributos en messages.properties


## In Progress

## Review

## Done

