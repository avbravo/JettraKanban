# JettraWebExample

## Backlog
### Modulo llamado Facturas
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T14:59:22.742184496" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
Crear un modulo llamado Facturas donde se tengan los entity, models, crudview
que se necesitan para un manejo de facturas. Productos, Clientes, Inventario, Ventas , Compras
Cuentas por pagar, cuentas por pagar, reportes, abonos. Crear los repository, controller(endpoint)


## To Do
### Configurar el baseUri en el archivo jettra-config.properties
<!-- jettra-meta created-by="avbravo" created-at="2026-05-26T15:18:13.872104784" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
En JettraWebExample analizar la clase
Analizar las clases RestClient y configurar el baseUri en el archivo jettra-config.properties 
baseUri = "http://localhost:8080/api/library/authors"


## In Progress

## Review

## Done
### Reoarganizar Model/Entity/Services
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T15:33:14.209579868" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
Analizar el uso de Entity a Model y Viceversa, puede ser una clase conversor.

### RestClient
<!-- jettra-meta created-by="avbravo" created-at="2026-05-26T14:24:26.441680687" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
En el proyecto JettraWebExample crea las clases RestClient para cada uno de los controller
que estan en el paquete 
com.jettra.example.controller.library;
Estos son clientes que se conecten a los enpoints y permiten realizar las operaciones
JettraRestClient debe contener su propia implementacion RestClient, si no la tiene
crea la implementación y documenta en JettraRest/guide/restclient.md

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

### com.jettra.example.services.library
<!-- jettra-meta created-by="avbravo" created-at="2026-05-26T15:37:51.069461567" sprint-id="afa147e0-0265-43b2-a019-ed664af6d198" -->
En el paquete com.jettra.example.services.library
Debe implementar clases para cada clase en el paquete RestClient y esta debe recibir un model y usar el converter para convertirlo a record y llamar a los metodos del RestClient y viceversa convertir de record a model segun sea el caso


