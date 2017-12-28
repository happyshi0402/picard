package picard.sam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Iso8601Date;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.cmdline.CommandLineProgram;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.programgroups.SamOrBam;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assigns all the reads in a file to a single new readgroup.
 *
 * <h3>Summary</h3>
 * <br />
 * Many tools (Picard and GATK for example) require or assume the presence of at least one RG tag, defining a "readgroup"
 * to which each read can be assigned (as specified in the RG tag in the SAM record).
 * This tool enables the user to assign all the reads in the {@link #INPUT} to a single new readgroup.
 * For more information about read groups, see the <a href='https://www.broadinstitute.org/gatk/guide/article?id=6472'>
 * GATK Dictionary entry.</a>
 * <br />
 * This tool accepts as INPUT BAM and SAM files or URLs from the Global Alliance for Genomics and Health (GA4GH) (see http://ga4gh.org/#/documentation).
 * <h4>Usage example:</h4>
 * <pre>
 * java -jar picard.jar AddOrReplaceReadGroups \\
 *       I=input.bam \\
 *       O=output.bam \\
 *       RGID=4 \\
 *       RGLB=lib1 \\
 *       RGPL=illumina \\
 *       RGPU=unit1 \\
 *       RGSM=20
 * </pre>
 * <br/>
 * <h3>Caveats</h3>
 * The value of the tags must adhere (according to the <a href=\"https://samtools.github.io/hts-specs/SAMv1.pdf\"> SAM-spec</a>)
 * with the regex '^[ -~]+$' In particular &lt;Space&gt; is the only non-printing character allowed.
 * <br/>
 * The program only enables the wholesale assignment of all the reads in the {@link #INPUT} to a single readgroup. If your file
 * already has reads assigned to multiple readgroups, the original RG value will be lost.
 *
 * @author mdepristo
 */
@CommandLineProgramProperties(
        summary = AddOrReplaceReadGroups.USAGE_SUMMARY + AddOrReplaceReadGroups.USAGE_DETAILS,
        oneLineSummary = AddOrReplaceReadGroups.USAGE_SUMMARY,
        programGroup = SamOrBam.class)
@DocumentedFeature
public class AddOrReplaceReadGroups extends CommandLineProgram {
    static final String USAGE_SUMMARY = "Add (if missing) or replaces the read groups in a BAM file with a new one.";
    static final String USAGE_DETAILS = "This tool enables the user to assign all the reads in the INPUT file to a single new readgroup." +
            "<br />" +
            "For more information about read groups, see the <a href='https://www.broadinstitute.org/gatk/guide/article?id=6472'>" +
            "GATK Dictionary entry.</a> " +
            "<br />" +
            "This tool accepts INPUT BAM and SAM files or URLs from the Global Alliance for Genomics and Health (GA4GH) (see http://ga4gh.org/#/documentation)." +
            "<h3>Usage example:</h3>" +
            "\n"+
            "java -jar picard.jar AddOrReplaceReadGroups \\\n" +
            "      I=input.bam \\\n" +
            "      O=output.bam \\\n" +
            "      RGID=4 \\\n" +
            "      RGLB=lib1 \\\n" +
            "      RGPL=illumina \\\n" +
            "      RGPU=unit1 \\\n" +
            "      RGSM=20 " +
            "</pre>" +
            "<br/>" +
            "<h3>Caveats</h3>" +
            "The value of the tags must adhere (according to the <a href=\"https://samtools.github.io/hts-specs/SAMv1.pdf\"> SAM-spec</a>) " +
            "with the RegEx '^[ -~]+$' In particular <Space> is the only non-printing character allowed." +
            "<br/> " +
            "The program only enables the wholesale assignment of all the reads in the INPUT to a single readgroup. If your file" +
            "already has reads assigned to multiple readgroups, the original RG value will be lost.";
    @Argument(shortName= StandardOptionDefinitions.INPUT_SHORT_NAME, doc="Input file (BAM or SAM or a GA4GH url).")
    public String INPUT = null;

    @Argument(shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc = "Output file (BAM or SAM).")
    public File OUTPUT = null;

    @Argument(shortName = StandardOptionDefinitions.SORT_ORDER_SHORT_NAME, optional = true,
            doc = "Optional sort order to output in. If not supplied OUTPUT is in the same order as INPUT.")
    public SortOrder SORT_ORDER;

    @Argument(shortName = "ID", doc = "Read Group ID")
    public String RGID = "1";

    @Argument(shortName = "LB", doc = "Read Group library")
    public String RGLB;

    @Argument(shortName = "PL", doc = "Read Group platform (e.g. illumina, solid)")
    public String RGPL;

    @Argument(shortName = "PU", doc = "Read Group platform unit (eg. run barcode)")
    public String RGPU;

    @Argument(shortName = "SM", doc = "Read Group sample name")
    public String RGSM;

    @Argument(shortName = "CN", doc = "Read Group sequencing center name", optional = true)
    public String RGCN;

    @Argument(shortName = "DS", doc = "Read Group description", optional = true)
    public String RGDS;

    @Argument(shortName = "DT", doc = "Read Group run date", optional = true)
    public Iso8601Date RGDT;

    @Argument(shortName = "KS", doc = "Read Group key sequence", optional = true)
    public String RGKS;

    @Argument(shortName = "FO", doc = "Read Group flow order", optional = true)
    public String RGFO;

    @Argument(shortName = "PI", doc = "Read Group predicted insert size", optional = true)
    public Integer RGPI;

    @Argument(shortName = "PG", doc = "Read Group program group", optional = true)
    public String RGPG;
    
    @Argument(shortName = "PM", doc = "Read Group platform model", optional = true)
    public String RGPM;

    private final Log log = Log.getInstance(AddOrReplaceReadGroups.class);

    protected int doWork() {
        IOUtil.assertInputIsValid(INPUT);
        IOUtil.assertFileIsWritable(OUTPUT);

        final SamReader in = SamReaderFactory.makeDefault()
            .referenceSequence(REFERENCE_SEQUENCE)
            .open(SamInputResource.of(INPUT));

        // create the read group we'll be using
        final SAMReadGroupRecord rg = new SAMReadGroupRecord(RGID);
        rg.setLibrary(RGLB);
        rg.setPlatform(RGPL);
        rg.setSample(RGSM);
        rg.setPlatformUnit(RGPU);
        if (RGCN != null) rg.setSequencingCenter(RGCN);
        if (RGDS != null) rg.setDescription(RGDS);
        if (RGDT != null) rg.setRunDate(RGDT);
        if (RGPI != null) rg.setPredictedMedianInsertSize(RGPI);
        if (RGPG != null) rg.setProgramGroup(RGPG);
        if (RGPM != null) rg.setPlatformModel(RGPM);
        if (RGKS != null) rg.setKeySequence(RGKS);
        if (RGFO != null) rg.setFlowOrder(RGFO);

        log.info(String.format("Created read group ID=%s PL=%s LB=%s SM=%s%n", rg.getId(), rg.getPlatform(), rg.getLibrary(), rg.getSample()));

        // create the new header and output file
        final SAMFileHeader inHeader = in.getFileHeader();
        final SAMFileHeader outHeader = inHeader.clone();
        outHeader.setReadGroups(Collections.singletonList(rg));
        if (SORT_ORDER != null) outHeader.setSortOrder(SORT_ORDER);

        final SAMFileWriter outWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(outHeader,
                outHeader.getSortOrder() == inHeader.getSortOrder(),
                OUTPUT);

        final ProgressLogger progress = new ProgressLogger(log);
        for (final SAMRecord read : in) {
            read.setAttribute(SAMTag.RG.name(), RGID);
            outWriter.addAlignment(read);
            progress.record(read);
        }

        // cleanup
        CloserUtil.close(in);
        outWriter.close();
        return 0;
    }

    @Override
    protected String[] customCommandLineValidation() {
        final List<String> values = new ArrayList<>();

        checkTagValue("RGID", RGID).ifPresent(values::add);
        checkTagValue("RGLB", RGLB).ifPresent(values::add);
        checkTagValue("RGPL", RGPL).ifPresent(values::add);
        checkTagValue("RGPU", RGPU).ifPresent(values::add);
        checkTagValue("RGSM", RGSM).ifPresent(values::add);
        checkTagValue("RGCN", RGCN).ifPresent(values::add);
        checkTagValue("RGDS", RGDS).ifPresent(values::add);
        checkTagValue("RGKS", RGKS).ifPresent(values::add);
        checkTagValue("RGFO", RGFO).ifPresent(values::add);
        checkTagValue("RGPG", RGPG).ifPresent(values::add);
        checkTagValue("RGPM", RGPM).ifPresent(values::add);

        if (!values.isEmpty()) {
            return values.toArray(new String[values.size()]);
        }

        return super.customCommandLineValidation();
    }

    private final Pattern pattern = Pattern.compile("^[ -~]+$");

    private Optional<String> checkTagValue(final String tagName, final String value) {
        if (value == null) {
            return Optional.empty();
        }

        final Matcher matcher = pattern.matcher(value);

        if (matcher.matches()) {
            return Optional.empty();
        } else {
            return Optional.of(String.format("The values of tags in a Sam header must adhere to the regex \"^[ -~]+$\", " +
                    "but the value provided for %s, '%s', doesn't.", tagName, value));
        }
    }
}
