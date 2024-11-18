package uk.gov.hmcts;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class Main {
    private static final CcdDb ccd = new CcdDb();

    public static void main(String[] args) throws IOException, SQLException, DocumentException {
        var inputDirectory = System.getProperty("user.home") + "/sscs-fix/downloads";
        var outputDirectory = System.getProperty("user.home") + "/sscs-fix/updated";
        var files = new java.io.File(inputDirectory).listFiles();
        for (var file : files) {
            if (file.isFile() && file.getName().endsWith(".pdf")) {
                updatePdf(file.getAbsolutePath(), outputDirectory + "/" + file.getName());
            }
        }

        ccd.close();
    }

    public static void updatePdf(String inputFilename, String outputFilename) throws IOException, SQLException, DocumentException {
        var reader = new PdfReader(inputFilename);

        var name = findText(reader, "\\sDear (.*)\\n", 1);
        if (name == null) {
            System.out.println("No name found for: " + inputFilename);
            return;
        }

        var caseReference = findText(reader, "Appeal reference number: (\\d+)", 1);

        if (caseReference == null) {
            caseReference = findText(reader, "(\\d{16})", reader.getNumberOfPages());

            if (caseReference == null) {
                System.out.println("No case reference found for: " + inputFilename);
                return;
            }
        }

        var postcode = ccd.getPostcodeAndDuplicateField(caseReference, name.trim());

        if (postcode == null) {
            postcode = findText(reader, "([A-Za-z][A-Ha-hJ-Yj-y]?[0-9][A-Za-z0-9]? ?[0-9][A-Za-z]{2}|[Gg][Ii][Rr] ?0[Aa]{2})", reader.getNumberOfPages());

            if (postcode == null || postcode.equals("CM20 9QF")) {
                System.out.println("No postcode found for: " + inputFilename);
                System.out.println("Name: " + name + ", Case reference: " + caseReference);
                return;
            }
        }

        var stamper = new PdfStamper(reader, new FileOutputStream(outputFilename));
        var canvas = stamper.getOverContent(1);
        var font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
        canvas.beginText();
        canvas.setFontAndSize(font, 8);

        var yOffset = 0;

        if (findText(reader, "We have booked a hearing for your", 1) != null) {
            yOffset = 4;
        } else if (findText(reader, "We have booked a hearing for", 1) != null) {
            yOffset = 4;
        } else if (findText(reader, "has told us they changed the decision about", 1) != null || findText(reader, "has told us they changed their decision about", 1) != null) {
            yOffset = 8;
        } else if (findText(reader, "You told us you wanted us to withdraw your", 1) != null) {
            yOffset = 8;
        } else if (findText(reader, "benefit appeal has been withdrawn.", 1) != null) {
            yOffset = 8;
        }

        canvas.setTextMatrix(77.5f, 664.5f - yOffset);
        canvas.showText(postcode);
        canvas.endText();
        stamper.close();
        reader.close();

        System.out.println(caseReference + "," + name + "," + postcode + "," + outputFilename);
    }

    public static String findText(PdfReader reader, String regex, int page) throws IOException {
        var contentParser = new PdfReaderContentParser(reader);
        var strategy = new LocationTextExtractionStrategy();
        var textStrategy = contentParser.processContent(page, strategy).getResultantText();
        var pattern = Pattern.compile(regex, Pattern.MULTILINE);
        var matcher = pattern.matcher(textStrategy);
        var found = matcher.find();
        return !found
            ? null
            : matcher.groupCount() > 0
                ? matcher.group(1)
                : "found";
    }
}