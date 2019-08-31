package mil.army.usace.hec.vortex.io;

import mil.army.usace.hec.vortex.GdalRegister;
import mil.army.usace.hec.vortex.VortexData;
import mil.army.usace.hec.vortex.VortexGrid;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.TranslateOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

class AscDataReader extends DataReader {

    static {
        GdalRegister.getInstance();
    }

    AscDataReader(DataReaderBuilder builder) {
        super(builder);
    }

    @Override
    public List<VortexData> getDTOs() {

        Dataset in = gdal.Open(path.toString());
        ArrayList<String>  options =  new ArrayList<>();
        options.add("-of");
        options.add("MEM");
        TranslateOptions translateOptions = new TranslateOptions(new Vector<>(options));
        Dataset raster = gdal.Translate("raster", in, translateOptions);
        raster.FlushCache();

        String fileName = path.getFileName().toString();
        String shortName;
        String fullName;
        String description;
        if (fileName.contains("ppt")){
            shortName = "precipitation";
            fullName = "precipitation";
            description = "precipitation";
        } else if (fileName.contains("tmean")){
            shortName = "mean temperature";
            fullName = "mean temperature";
            description = "mean temperature";
        } else if (fileName.contains("tmin")){
            shortName = "minimum temperature";
            fullName = "minimum temperature";
            description = "minimum temperature";
        } else if (fileName.contains("tmax")){
            shortName = "maximum temperature";
            fullName = "maximum temperature";
            description = "maximum temperature";
        } else if (fileName.contains("tdmean")){
            shortName = "mean dewpoint temperature";
            fullName = "mean dewpoint temperature";
            description = "mean dewpoint temperature";
        } else if (fileName.contains("vpdmin")){
            shortName = "minimum vapor pressure deficit";
            fullName = "minimum vapor pressure deficit";
            description = "minimum vapor pressure deficit";
        } else if (fileName.contains("vpdmax")){
            shortName = "maximum vapor pressure deficit";
            fullName = "maximum vapor pressure deficit";
            description = "maximum vapor pressure deficit";
        }   else {
            shortName = "";
            fullName = "";
            description = "";
        }

        String string1 = path.toString();
        String string2 = string1.substring(0, string1.lastIndexOf('_'));
        String string3 = string2.substring(string2.length() - 8);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate date = LocalDate.parse(string3, formatter);
        ZonedDateTime startTime = ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(0, 0)), ZoneId.of("UTC"));
        ZonedDateTime endTime = ZonedDateTime.of(LocalDateTime.of(date.plusDays(1), LocalTime.of(0, 0)), ZoneId.of("UTC"));

        String units;
        if (fileName.contains("ppt")){
            units = "mm";
        } else if (fileName.contains("tmean")){
            units = "Degrees C";
        } else if (fileName.contains("tmin")){
            units = "Degrees C";
        } else if (fileName.contains("tmax")){
            units = "Degrees C";
        } else if (fileName.contains("tdmean")){
            units = "Degrees C";
        } else if (fileName.contains("vpdmin")){
            units = "hPa";
        } else if (fileName.contains("vpdmax")){
            units = "hPa";
        } else {
            units = "";
        }

        double[] geoTransform = raster.GetGeoTransform();
        double dx = geoTransform[1];
        double dy = geoTransform[5];
        double ulx = geoTransform[0];
        double uly = geoTransform[3];
        int nx = raster.GetRasterXSize();
        int ny = raster.GetRasterYSize();
        String wkt = raster.GetProjection();
        Band band = raster.GetRasterBand(1);
        float[] data = new float[nx * ny];
        band.ReadRaster(0, 0, nx, ny, gdalconst.GDT_Float32, data);

        raster.delete();
        band.delete();

        VortexGrid dto = VortexGrid.builder()
                .dx(dx).dy(dy).nx(nx).ny(ny)
                .originX(ulx).originY(uly)
                .wkt(wkt).data(data).units(units)
                .fileName(path.toString()).shortName(shortName)
                .fullName(fullName).description(description)
                .startTime(startTime).endTime(endTime)
                .interval(Duration.between(startTime, endTime))
                .build();

        List<VortexData> list = new ArrayList<>();
        list.add(dto);
        return list;
    }

    public static Set<String> getVariables(Path pathToAsc){
        String fileName = pathToAsc.getFileName().toString();
        if (fileName.startsWith("PRISM_ppt")) {
            return new HashSet<>(Collections.singletonList("ppt"));
        }
        if (fileName.startsWith("PRISM_tmean")) {
            return new HashSet<>(Collections.singletonList("tmean"));
        }
        if (fileName.startsWith("PRISM_tmin")) {
            return new HashSet<>(Collections.singletonList("tmin"));
        }
        if (fileName.startsWith("PRISM_tmax")) {
            return new HashSet<>(Collections.singletonList("tmax"));
        }
        if (fileName.startsWith("PRISM_tdmean")) {
            return new HashSet<>(Collections.singletonList("tdmean"));
        }
        if (fileName.startsWith("PRISM_vpdmin")) {
            return new HashSet<>(Collections.singletonList("vpdmin"));
        }
        if (fileName.startsWith("PRISM_vpdmax")) {
            return new HashSet<>(Collections.singletonList("vpdmax"));
        }
        return new HashSet<>(Collections.singletonList(fileName.substring(0, fileName.lastIndexOf('.'))));
    }
}
