# JettraFileSystem

## Backlog
### En JettraChunk
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:23:35.554910205" -->
En JettraChunks opttimizar el algoritmo de compresion y dividsion en chunks para que sea mas eficiente rapido y el tamaño sea menor.

### Crear un plafinicador de Virtual Thread
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:23:51.538243681" -->
Crear un plafinicador de Virtual Thread por archivo a procesar, determina cuantos Trhead son necesarios para el envio de los chunks al receptor optimizando los recursos y cuando un chubk se recibe en el receptor se elimina del sender.

### Recuerda que las acciones de copiar y
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:24:06.474897042" -->
Recuerda que las acciones de copiar y mover no deben tener en cuanta el directorio .jettra_sender_temp ni .jettra_receptor_temp

### proceso detector que determina si hay otras instancias de JettraFileSystem
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:24:33.594366009" -->
Añadir un proceso detector que determina si hay otras instancias de JettraFileSystem en ejecucion

### inicia el proceso de copiar o mover, debe crear la carpeta .jettra_sende
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:24:54.889012979" -->
Cuando inicia el proceso de copiar o mover, debe crear la carpeta .jettra_sender_temp que es un carpeta temporal donde se almacenan subcarpetas que contendran los subdirectorios generados mediante UUID correspondientes a cada archivo que se procesa. En el cliente que recibe el archivo se crea el directorio .jettra_receptor_temp donde se crean subcarpetas correspondientes a los archivos enviados desde el sender (es decir los chunks por cada archivo que se generan subcarpetas con el correspondiente UUID). Ten presente que cuando el usuario da clic en Copiar o Mover, la carpeta .jettra_sender_temp o .jettra_recpetor_temp son ignoradas ya que se ejecutan de manera directa.

### Cuando finaliza la operacion de transferencia sea po
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:25:08.619927174" -->
Cuando finaliza la operacion de transferencia sea por copiar o mover las subcarpetas correspondientes a .jetrra_sender_temp y .jettra_receptor_temp se deben eliminar con el correspondiente subdirectorio y chunks de cada archivo completado.

### Cuando se detiene o inicia la ejecucion
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:25:25.219390899" -->
Cuando se detiene o inicia la ejecucion se verifica que no exista otra instancia de JettraFileSystem en ejecucion y se procede a eliminar la la carpeta .jettra_sender_temp del sender y en el receptor se debe eliminar la carpeta .jettra_receptor_temp.

### Debe mostrar en la seccion Destino,
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:25:38.594756473" -->
Debe mostrar en la seccion Destino, es que actulice en tiempo real, el arbol con los directorios y archivos que se van transfiriendo

### Realiza la copia de archivos y la operaicon mov
<!-- jettra-meta created-by="avbravo" created-at="2026-05-25T09:25:50.195880841" -->
Realiza la copia de archivos y la operaicon mover en paralelo usando Virtual Thread , para enviar muchos chunks en tiempo real.


## To Do

## In Progress

## Review

## Done

