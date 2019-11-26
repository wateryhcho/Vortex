package mil.army.usace.hec.vortex.io;

import mil.army.usace.hec.vortex.GdalRegister;
import mil.army.usace.hec.vortex.VortexData;
import mil.army.usace.hec.vortex.VortexGrid;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.TranslateOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class SnodasTarDataReader extends DataReader implements VirtualFileSystem{
    static {GdalRegister.getInstance();}
    SnodasTarDataReader(DataReaderBuilder builder) {super(builder);}

    @Override
    // Return a list of data-transferable-objects for the specified variable (SWE/Liquid Precipitaion/etc...)
    public List<VortexData> getDtos() {
        // Get VirtualPath to Tar
        String folderName = Paths.get(this.path).getFileName().toString();
        folderName = folderName.substring(0, folderName.lastIndexOf(".tar")) + "_unzip";
        String folderPath = Paths.get(this.path).getParent().toString() + File.separator + folderName + File.separator;
        // Use Gdal to read in data
        Vector fileList = gdal.ReadDir(folderPath);
        List<VortexData> dtos = new ArrayList<>();
        for(Object o : fileList) {
            String fileName = o.toString();
            if (fileName.endsWith(".dat")) {
                DataReader reader = DataReader.builder()
                        .path(folderPath + fileName)
                        .variable(this.variableName)
                        .build();

                dtos.addAll(reader.getDtos());
            } // Extract grid from .dat files
        } // Loop through tar

        return dtos;
    } // getDtos()

    @Override
    public int getDtoCount() {
        // Pre-processing: Extracting files from the specified tar, and decompress them
        try {updateTar(this.path);} catch (IOException e) {e.printStackTrace();}
        // Get the number of .dat files that matches with the specified variableName (this.variable)
        return 1;
    } // getDtoCount()

    @Override
    public VortexData getDto(int idx) {
         int count = -1;
         String folderName = Paths.get(this.path).getFileName().toString();
         folderName = folderName.substring(0, folderName.lastIndexOf(".tar")) + "_unzip";
         String folderPath = Paths.get(this.path).getParent().toString() + File.separator + folderName + File.separator;
         String pathToFolder = Paths.get(folderPath).getParent().toString();
         File folder = new File(pathToFolder, folderName);

        for (File fileEntry : Objects.requireNonNull(folder.listFiles())) {
            String fileName = fileEntry.getName();
            if(fileName.endsWith(".dat")) {
                if (matchedVariable(fileName, this.variableName)) {
                    count++; // First file, count = 0. Second, count = 1, etc...
                    if (count == idx) {
                        DataReader reader = DataReader.builder()
                                .path(folder.getAbsolutePath() + File.separator + fileName)
                                .variable(this.variableName)
                                .build();

                        return reader.getDto(0);
                    }
                }
            }
        } // Looping through folder to get Dto

        return null;
    } // getDto()

    private void updateTar(String pathToFile) throws IOException {
        // Get DirectoryPath, TarFilePath, and TarFileName
        String tarFilePath = pathToFile;
        String directoryPath = Paths.get(tarFilePath).getParent().toString();
        String tarFileName = Paths.get(tarFilePath).getFileName().toString();
        // Creating an input stream for the original tar file
        File originalTarFile = new File(directoryPath, tarFileName);
        TarArchiveInputStream originalStream = new TarArchiveInputStream(new FileInputStream(originalTarFile));

        // Extract files from tar into untarFolder
        String tarName = tarFileName.substring(0, tarFileName.lastIndexOf(".tar"));
        File untarFolder = unTarFile(directoryPath, originalStream, tarName);
        // Close originalStream
        originalStream.close();
        // Decompress GZ files inside untarFolder to gzFolder
        File gzFolder = decompressFolder(directoryPath, untarFolder, tarName);
        // Create header files
        for(File fileEntry : gzFolder.listFiles()) {
            String fileName = fileEntry.getName();
            if(fileName.endsWith(".dat")) {
                String name = fileName.substring(0, fileName.lastIndexOf(".dat")) + ".hdr";
                String folderName = tarName + "_unzip";
                String gzFolderPath = directoryPath + File.separator + folderName;
                createHeader(gzFolderPath, name);
            }
        } // Loop through unzipped Folder

        // Clean up: deleting untarFolder
        FileUtils.deleteDirectory(untarFolder);
    } // updateTar()

    private boolean matchedVariable(String fileName, String variableName) {
        String productCode = fileName.substring(8,12);
        String dataType = fileName.substring(13,17);

        if(productCode.equals("1034") && variableName.equals("SWE"))
            return true;
        else if(productCode.equals("1036") && variableName.equals("Snow Depth"))
            return true;
        else if(productCode.equals("1044") && variableName.equals("Snow Melt Runoff at the Base of the Snow Pack"))
            return true;
        else if(productCode.equals("1050") && variableName.equals("Sublimation from the Snow Pack"))
            return true;
        else if(productCode.equals("1039") && variableName.equals("Sublimation of Blowing Snow"))
            return true;
        else if(productCode.equals("1025") && dataType.equals("lL01") && variableName.equals("Solid Precipitation"))
            return true;
        else if(productCode.equals("1025") && dataType.equals("lL00") && variableName.equals("Liquid Precipitation"))
            return true;
        else if(productCode.equals("1038") && variableName.equals("Snow Pack Average Temperature"))
            return true;
        else
            return false;
    } // matchedVariable() returns true if fileName matches with the variableName


    private File tarFolder(String directoryPath, File inputFolder) throws IOException {
        // File tempTarFile = tarFolder(directoryPath, gzFolder);
        // ^ Use that line in updateTar to compress the zip folder into a tar
        // Create a temp Tar File
        File tempTarFile = new File(directoryPath, "tempTar.tar");
        tempTarFile.createNewFile();
        TarArchiveOutputStream newStream = new TarArchiveOutputStream(new FileOutputStream(tempTarFile));

        for(File currentFile : inputFolder.listFiles()) {
            ArchiveEntry entry = newStream.createArchiveEntry(currentFile, currentFile.getName());
            newStream.putArchiveEntry(entry);
            InputStream folderStream = Files.newInputStream(currentFile.toPath());
            IOUtils.copy(folderStream, newStream);
            folderStream.close();
            newStream.closeArchiveEntry();
        }
        newStream.close();

        return tempTarFile;
    } // tarFolder

    private File unTarFile(String directoryPath, TarArchiveInputStream iStream, String tarName) throws IOException {
        String fileSeparator = File.separator;
        ArchiveEntry nextEntry = null;
        // Creating a folder to untar into
        String folderPath = directoryPath + fileSeparator + tarName + "_untar";
        File destinationFolder = new File(folderPath);
        if(!destinationFolder.exists())
            destinationFolder.mkdirs();

        // Untar into folder
        while((nextEntry = iStream.getNextEntry()) != null) {
            File outputFile = new File(destinationFolder + fileSeparator + nextEntry.getName());
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            IOUtils.copy(iStream, outputStream);
            outputStream.close();
        } // Untar into folder

        return destinationFolder;
    } // unTarFile

    private File decompressFolder(String directoryPath, File inputFolder, String tarName) throws IOException {
        String fileSeparator = File.separator;
        // Creating a folder to unzip into
        String gzFolderPath = directoryPath + fileSeparator + tarName + "_unzip";
        File gzDestinationFolder = new File(gzFolderPath);
        if(!gzDestinationFolder.exists())
            gzDestinationFolder.mkdirs();

        // Loop through inputFolder
        if(inputFolder.isDirectory()) {
            for (File fileEntry : Objects.requireNonNull(inputFolder.listFiles())) {
                String name = fileEntry.getName().substring(0, fileEntry.getName().lastIndexOf(".gz"));
                File decompressedOutputFile = new File(gzDestinationFolder + fileSeparator + name);
                GzipCompressorInputStream gzStream = new GzipCompressorInputStream(new FileInputStream(fileEntry));
                FileOutputStream decompressedOutputStream = new FileOutputStream(decompressedOutputFile);
                IOUtils.copy(gzStream, decompressedOutputStream);
                gzStream.close();
                decompressedOutputStream.close();
            }
        } // unzipFolder contains decompressed files

        return gzDestinationFolder;
    } // decompressFolder

    private File createHeader(String directoryPath, String headerName) throws IOException {
        // Create empty header file
        File headerFile = new File(directoryPath, headerName);
        // Content of header
        List<String> enviHeader = Arrays.asList("ENVI", "samples = 6935", "lines = 3351", "bands = 1",
                "header offset = 0", "file type = ENVI Standard", "data type = 2", "interleave = bsq", "byte order = 1");
        // Write to HeaderFile
        Path header = Paths.get(directoryPath + File.separator + headerName);
        Files.write(header, enviHeader, StandardCharsets.UTF_8);

        return headerFile;
    } // createHeader()
} // SnodasTarDataReader class