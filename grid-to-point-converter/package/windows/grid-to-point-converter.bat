SET "PATH=.\gdal;%PATH%"
SET "GDAL_DRIVER_PATH=.\gdal\gdalplugins"
SET "GDAL_DATA=.\gdal\gdal-data"
SET "PROJ_LIB=.\gdal\projlib"
"..\jre\bin\java.exe" -Djava.library.path=".;.\gdal" -cp ..\lib\grid-to-point-converter.jar;..\lib\* converter.ConverterWizard