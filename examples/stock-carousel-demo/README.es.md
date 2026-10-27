# Ejemplo del carrusel del launcher original

[English](README.md)

Este ejemplo añade **una página con contador inmediatamente después del reloj
original** del Xiaomi X04G. Las demás páginas conservan su orden relativo. Usa
Views de Android y un módulo Zygisk; no sustituye HOME, no vuelve a firmar el
launcher ni distribuye APK OEM, fuentes extraídas o firmware.

El reloj ya debe tener root, ADB por USB y un entorno Zygisk compatible con API 4.
Este ejemplo no desbloquea el arranque, modifica ni flashea boot, configura root
o abre una consola de administrador por red.

Usa un único propietario del pager original. El instalador rechaza la extensión
anterior conocida `x04g_home_bootstrap_direct01`, incluso si está desactivada.
Este ejemplo no migra esa extensión ni su configuración.

## Compatibilidad

El adaptador por reflexión admite deliberadamente una versión concreta del
launcher. Debe rechazar otra versión en lugar de adivinar sus campos privados.

| Elemento | Valor esperado |
| --- | --- |
| Dispositivo | Xiaomi Mi Smart Clock X04G |
| Android | 10 / API 29 |
| ABI del proceso | ARM de 32 bits / `armeabi-v7a` |
| Pantalla de referencia | 800×480 |
| Paquete OEM | `com.google.assistant.launcher` |
| Proceso compartido | `com.google.android.apps.mediashell` |
| Activity | `com.google.assistant.surfaces.clock.launcher.AssistantLauncherActivity` |
| APK OEM | `/product/app/com.google.assistant.launcher/app.apk` |
| Tamaño del APK | 33126600 bytes |
| SHA256 del APK | `1e5b52f356ef7c078619dc369cccf704060aca484754cef454fa921598ed8c21` |
| Versión del launcher | 111706 / `11.51.6.416171605` |
| UID | Se obtiene al instalar; no se copia desde otro dispositivo |

El instalador comprueba dispositivo/API/ABI, hash exacto del APK OEM y código de
versión. Después guarda el UID encontrado en `uid.pin`; el cargador lo contrasta
para seleccionar el proceso. El header público Zygisk API 4 está fijado al commit
upstream `7bb941ac8edfcffd1d23761e401c45ca95409dc1`, SHA256
`fc6523882c0f8659b4fcd1e04ffb07d3a948090e5a8c08817af6e2b506a078d0`.
Son identificadores de compatibilidad, no una afirmación de que otro firmware
X04G sea equivalente.

## Compilar localmente

Se necesitan Python 3, JDK 17 o posterior con `javac`, Android SDK con plataforma
29, build-tools recientes con **D8/R8 8.x o posterior** y `core-lambda-stubs.jar`,
y Android NDK con compilador ARM32. API 29 es la plataforma de destino; no implica
usar el D8 antiguo del SDK 29, que falló con este ejemplo. También se necesita
`core-lambda-stubs.jar` para que `javac` compile las lambdas de Java 8 contra el
bootclasspath de API 29. No se incluyen herramientas.

La compilación HOST usó Linux/WSL, JDK 21, plataforma Android 29, D8/R8 8.3.37 y
NDK r20b. El destino nativo es `armv7a-linux-androideabi29`, sin un runtime de C++
incluido. Si ese NDK no trae `llvm-readelf`, el builder puede usar `readelf` de
`PATH`. Detectar herramientas de otros sistemas no acredita una compilación en
ellos.

Desde esta carpeta, verifica las rutas sin compilar. `--check` no ejecuta un
compilador; la compatibilidad de versión de D8 se comprueba al compilar:

```sh
python3 build.py --sdk /ruta/Android/Sdk --ndk /ruta/ndk --jdk /ruta/jdk --check
```

La siguiente invocación inicia explícitamente la compilación:

```sh
python3 build.py --sdk /ruta/Android/Sdk --ndk /ruta/ndk --jdk /ruta/jdk
```

Para otra estructura del SDK, `--android-jar` selecciona el JAR de plataforma API
29 y `--d8` selecciona un JAR de D8/R8. Normalmente se encuentra
`core-lambda-stubs.jar` junto a la carpeta `lib/` de build-tools que contiene D8.
Si usas un JAR de D8/R8 independiente fuera de esa estructura, proporciona los
stubs expresamente, por ejemplo:

```sh
python3 build.py \
  --sdk /ruta/Android/Sdk \
  --ndk /ruta/ndk \
  --jdk /ruta/jdk \
  --android-jar /ruta/Android/Sdk/platforms/android-29/android.jar \
  --d8 /ruta/r8lib-8.3.37.jar \
  --core-lambda-stubs /ruta/Android/Sdk/build-tools/VERSION/core-lambda-stubs.jar
```

Sustituye `VERSION` por una versión instalada de build-tools. `--out` selecciona
la carpeta de salida; por defecto es `build/`. El resultado es:

```text
build/x04g-carousel-demo-disabled.zip
```

`build/build-report.json` registra las entradas de compilación y el hash final.

El ZIP contiene el DEX autónomo y el puente ARM32. **No hay un APK compañero que
instalar.** Incluye `disable` y `skip_mount`: instalarlo no activa su carga.
Conserva `skip_mount`; para activarlo sólo se elimina `disable`.

## Instalar, inspeccionar y activar

Ejecuta los comandos en un **shell POSIX**, como Linux o WSL. Usan **ADB por USB**
(`-d`), y el root existente debe permitir `su`. Las comillas anidadas conservan
la cadena completa de cada `su -c` en Android. No habilitan ADB por red ni una
consola root por red.

```sh
adb -d shell su -c id
adb -d push build/x04g-carousel-demo-disabled.zip /data/local/tmp/x04g-carousel-demo-disabled.zip
adb -d shell 'su -c "magisk --install-module /data/local/tmp/x04g-carousel-demo-disabled.zip"'
adb -d reboot
```

Este primer reinicio aplica el módulo **desactivado**. El [instalador de Magisk 26.1](https://github.com/topjohnwu/Magisk/blob/v26.1/scripts/util_functions.sh#L680-L740)
prepara la instalación desde Android en `/data/adb/modules_update/`; por eso `uid.pin` no
tiene por qué estar inmediatamente en el directorio activo del módulo. Cuando
Android y ADB por USB regresen, inspecciona el módulo antes de activarlo:

```sh
adb -d shell pm list packages -U com.google.assistant.launcher
adb -d shell 'su -c "cat /data/adb/modules/x04g_carousel_demo/uid.pin"'
adb -d shell 'su -c "ls -l /data/adb/modules/x04g_carousel_demo/disable /data/adb/modules/x04g_carousel_demo/skip_mount"'
```

El UID del paquete debe coincidir con `uid.pin`. Si el instalador rechaza el
firmware o los valores difieren, deja el módulo desactivado; editar un pin no
resuelve una incompatibilidad.

Actívalo expresamente y reinicia una segunda vez para cargar el módulo:

```sh
adb -d shell 'su -c "rm /data/adb/modules/x04g_carousel_demo/disable"'
adb -d reboot
```

Después del arranque, desliza desde el reloj original hasta la página añadida y
pulsa el botón del contador. Comprueba que sigan disponibles las páginas y los
menús originales. El contador sólo vive en memoria. No se inician cámaras,
audio, red ni RF, y el retorno automático al reloj queda a cargo del sistema OEM.

## Desactivar

Con ADB por USB disponible, marca el módulo como desactivado y reinicia:

```sh
adb -d shell 'su -c "touch /data/adb/modules/x04g_carousel_demo/disable"'
adb -d reboot
```

La extensión deja de cargarse en el siguiente arranque del proceso. Esto no
restaura ni flashea firmware y no es un procedimiento de recuperación si se
pierde el acceso ADB/root.

## Integración con el launcher original

El puente nativo selecciona el proceso ARM32 verificado y carga el DEX del
ejemplo. Java espera a que existan Application y Looper principal antes de
comprobar el APK original y adjuntar las páginas en el hilo principal.
`OemCarousel` localiza por reflexión el pager, adaptador y registro de fragmentos
existentes. Añade un fragmento/modelo propio después del reloj conservando la
identidad y el orden relativo de los modelos ajenos.

Se emplean un identificador de página propio y widgets normales de Android. No
se reemplaza el adaptador original por otro launcher. Los nombres privados de
clases/campos, la propiedad de los fragmentos y las transiciones de Activity
forman parte del límite de compatibilidad; no son una API pública del fabricante.

Para reutilizar una interfaz de tu app, sustituye el cuerpo de
`DemoSession.createView(Activity)` en `java/org/x04g/carouseldemo/DemoSession.java`:

```java
FrameLayout page = new FrameLayout(activity);
page.addView(new MyDashboardView(activity),
        new FrameLayout.LayoutParams(-1, -1));
return page;
```

Incluye `MyDashboardView` en el árbol Java del ejemplo. Elimina o sustituye también
el campo del contador y las líneas que lo usan en `selected()` y `close()`; de lo
contrario esos métodos seguirían esperando el botón anterior. Conserva el manejo
de visibilidad y cierre para tu nueva View. Instalar otro APK por
sí solo no añade su Activity al carrusel original. Este código corre en el
proceso/UID OEM: los permisos, almacenamiento y claves de una app independiente
no se transfieren automáticamente. Si añades trabajo aparte del contador,
inícialo únicamente cuando la página sea realmente visible y libéralo al ocultar
o cerrar la página. Usa recursos gráficos propios o con una licencia adecuada.

## Alcance de validación

La implementación de dos páginas de la que se redujo este ejemplo se observó
en un X04G físico con este launcher. **Este módulo reducido de una página no se
ha instalado ni aceptado por sí mismo en un reloj físico.** Su compilación,
comprobaciones de paquete y fixtures de orden en HOST son resultados separados;
véase [tests/README.md](tests/README.md). Compilar no acredita los toques reales,
la carga Zygisk/Magisk o el ciclo de vida OEM.

Esta contribución no acepta la recreación de Activity. El antecedente mayor
mostró un posible problema de sesión retenida/limpieza tras una transición; la
espera de arranque no es una solución demostrada de ese fallo. Actualizaciones
de firmware, retorno automático y retirada/recreación requieren sus propias
pruebas físicas. No se garantiza estabilidad prolongada, recuperación completa
ni conservación probada de todas las funciones originales.

## Licencia y material de terceros

El código y documentación originales **de esta carpeta de ejemplo** se ofrecen
bajo [MIT](LICENSE). El header Zygisk conserva su propio aviso; véase
[NOTICE.md](NOTICE.md). Esto no cambia la licencia del resto del repositorio. No
se incluyen recursos OEM ni binarios de las herramientas de compilación.

Consulta [VALIDATION.md](VALIDATION.md) para distinguir las comprobaciones completadas de las pruebas pendientes en hardware.
