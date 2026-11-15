package com.example.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PresentationSlide(
    val title: String,
    val bullets: List<String>,
    val keyTakeaway: String? = null,
    val speakerNotes: String? = null
)

data class GeneratedDeck(
    val deckTitle: String,
    val subtitle: String,
    val accentColorHex: String = "00B4D8",
    val slides: List<PresentationSlide>
)

object PptxGenerator {

    private const val SLIDE_WIDTH_EMU = 12192000L // 16:9 widescreen (13.333 in)
    private const val SLIDE_HEIGHT_EMU = 6858000L // 7.5 in

    suspend fun createPptxFile(context: Context, deck: GeneratedDeck): File =
        withContext(Dispatchers.IO) {
            val dir = AttachmentHelper.getGeneratedPptDir(context)
            val safeBase = deck.deckTitle
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "jarvis_deck" }
                .take(36)
            val fileName = "${safeBase}_${UUID.randomUUID().toString().take(6)}.pptx"
            val outFile = File(dir, fileName)
            val bytes = buildPptxBytes(deck)
            FileOutputStream(outFile).use { it.write(bytes) }
            outFile
        }

    internal fun buildPptxBytes(deck: GeneratedDeck): ByteArray {
        val totalSlides = 1 + deck.slides.size
        val accentHex = normalizeHex(deck.accentColorHex, "00B4D8")
        val baos = ByteArrayOutputStream()

        ZipOutputStream(baos).use { zip ->
            zip.putXml("[Content_Types].xml", buildContentTypesXml(totalSlides))
            zip.putXml("_rels/.rels", buildRootRelsXml())
            zip.putXml("ppt/presentation.xml", buildPresentationXml(totalSlides))
            zip.putXml("ppt/_rels/presentation.xml.rels", buildPresentationRelsXml(totalSlides))
            zip.putXml("ppt/presProps.xml", buildPresPropsXml())
            zip.putXml("ppt/viewProps.xml", buildViewPropsXml())
            zip.putXml("ppt/tableStyles.xml", buildTableStylesXml())
            zip.putXml("ppt/theme/theme1.xml", buildThemeXml(accentHex))
            zip.putXml("ppt/slideMasters/slideMaster1.xml", buildSlideMasterXml())
            zip.putXml("ppt/slideMasters/_rels/slideMaster1.xml.rels", buildSlideMasterRelsXml())
            zip.putXml("ppt/slideLayouts/slideLayout1.xml", buildSlideLayoutXml())
            zip.putXml("ppt/slideLayouts/_rels/slideLayout1.xml.rels", buildSlideLayoutRelsXml())

            // Slide 1: Cover Slide
            zip.putXml(
                "ppt/slides/slide1.xml",
                buildCoverSlideXml(
                    title = deck.deckTitle,
                    subtitle = deck.subtitle,
                    accentHex = accentHex,
                    slideCount = totalSlides
                )
            )
            zip.putXml("ppt/slides/_rels/slide1.xml.rels", buildSlideRelsXml())

            // Slides 2..N: Content Slides
            deck.slides.forEachIndexed { idx, slide ->
                val slideNumber = idx + 2
                zip.putXml(
                    "ppt/slides/slide$slideNumber.xml",
                    buildContentSlideXml(
                        deckTitle = deck.deckTitle,
                        slide = slide,
                        slideNumber = slideNumber,
                        totalSlides = totalSlides,
                        accentHex = accentHex
                    )
                )
                zip.putXml("ppt/slides/_rels/slide$slideNumber.xml.rels", buildSlideRelsXml())
            }
        }

        return baos.toByteArray()
    }

    private fun ZipOutputStream.putXml(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.trimStart().toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun buildContentTypesXml(totalSlides: Int): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
        appendLine("""  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        appendLine("""  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
        appendLine("""  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
        appendLine("""  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>""")
        appendLine("""  <Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/>""")
        appendLine("""  <Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/>""")
        appendLine("""  <Override PartName="/ppt/tableStyles.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"/>""")
        for (i in 1..totalSlides) {
            appendLine("""  <Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildPresentationXml(totalSlides: Int): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        appendLine("""  <p:sldMasterIdLst>""")
        appendLine("""    <p:sldMasterId id="2147483648" r:id="rId1"/>""")
        appendLine("""  </p:sldMasterIdLst>""")
        appendLine("""  <p:sldIdLst>""")
        for (i in 1..totalSlides) {
            val sldId = 255 + i
            val rId = i + 4
            appendLine("""    <p:sldId id="$sldId" r:id="rId$rId"/>""")
        }
        appendLine("""  </p:sldIdLst>""")
        appendLine("""  <p:sldSz cx="$SLIDE_WIDTH_EMU" cy="$SLIDE_HEIGHT_EMU"/>""")
        appendLine("""  <p:notesSz cx="6858000" cy="9144000"/>""")
        append("""</p:presentation>""")
    }

    private fun buildPresentationRelsXml(totalSlides: Int): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        appendLine("""  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
        appendLine("""  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps" Target="presProps.xml"/>""")
        appendLine("""  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps" Target="viewProps.xml"/>""")
        appendLine("""  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>""")
        appendLine("""  <Relationship Id="rId${totalSlides + 5}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles" Target="tableStyles.xml"/>""")
        for (i in 1..totalSlides) {
            val rId = i + 4
            appendLine("""  <Relationship Id="rId$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildPresPropsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentationPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>
    """.trimIndent()

    private fun buildViewPropsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:viewPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:normalViewPr><p:restoredLeft sz="15620"/><p:restoredTop sz="94660"/></p:normalViewPr>
        </p:viewPr>
    """.trimIndent()

    private fun buildTableStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:tblStyleLst xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>
    """.trimIndent()

    private fun buildThemeXml(accentHex: String): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Jarvis Modern">
          <a:themeElements>
            <a:clrScheme name="Jarvis">
              <a:dk1><a:srgbClr val="0F172A"/></a:dk1>
              <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
              <a:dk2><a:srgbClr val="1E293B"/></a:dk2>
              <a:lt2><a:srgbClr val="F8FAFC"/></a:lt2>
              <a:accent1><a:srgbClr val="$accentHex"/></a:accent1>
              <a:accent2><a:srgbClr val="F59E0B"/></a:accent2>
              <a:accent3><a:srgbClr val="10B981"/></a:accent3>
              <a:accent4><a:srgbClr val="6366F1"/></a:accent4>
              <a:accent5><a:srgbClr val="0EA5E9"/></a:accent5>
              <a:accent6><a:srgbClr val="EC4899"/></a:accent6>
              <a:hlink><a:srgbClr val="0284C7"/></a:hlink>
              <a:folHlink><a:srgbClr val="7C3AED"/></a:folHlink>
            </a:clrScheme>
            <a:fontScheme name="JarvisFonts">
              <a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
              <a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
            </a:fontScheme>
            <a:fmtScheme name="JarvisFmt">
              <a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
              <a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
              <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
              <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
            </a:fmtScheme>
          </a:themeElements>
        </a:theme>
    """.trimIndent()

    private fun buildSlideMasterXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
            </p:spTree>
          </p:cSld>
          <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
          <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
        </p:sldMaster>
    """.trimIndent()

    private fun buildSlideMasterRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildSlideLayoutXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
          <p:cSld name="Blank">
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
            </p:spTree>
          </p:cSld>
          <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
        </p:sldLayout>
    """.trimIndent()

    private fun buildSlideLayoutRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildSlideRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildCoverSlideXml(
        title: String,
        subtitle: String,
        accentHex: String,
        slideCount: Int
    ): String {
        val safeTitle = escapeXml(title)
        val safeSubtitle = escapeXml(subtitle.ifBlank { "Generated by Jarvis AI • $slideCount Slides" })
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            append(
                """
              <p:cSld>
                <p:bg>
                  <p:bgPr>
                    <a:solidFill><a:srgbClr val="0B1220"/></a:solidFill>
                    <a:effectLst/>
                  </p:bgPr>
                </p:bg>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
                  ${buildRectShape(id = 2, name = "TopAccent", x = 0, y = 0, cx = SLIDE_WIDTH_EMU, cy = 140000, fillHex = accentHex)}
                  ${buildRectShape(id = 3, name = "LeftGlowBar", x = 820000, y = 1950000, cx = 110000, cy = 2400000, fillHex = accentHex)}
                  ${buildTextBox(
                      id = 4,
                      name = "CoverTitle",
                      x = 1100000,
                      y = 1900000,
                      cx = 10200000,
                      cy = 1600000,
                      paragraphs = listOf(
                          ParagraphSpec(text = safeTitle, fontSizePt = 40, bold = true, colorHex = "F8FAFC", spaceAfterPt = 12)
                      )
                  )}
                  ${buildTextBox(
                      id = 5,
                      name = "CoverSubtitle",
                      x = 1100000,
                      y = 3550000,
                      cx = 9800000,
                      cy = 1100000,
                      paragraphs = listOf(
                          ParagraphSpec(text = safeSubtitle, fontSizePt = 20, bold = false, colorHex = "94A3B8", spaceAfterPt = 6)
                      )
                  )}
                  ${buildTextBox(
                      id = 6,
                      name = "CoverFooter",
                      x = 1100000,
                      y = 5950000,
                      cx = 9800000,
                      cy = 450000,
                      paragraphs = listOf(
                          ParagraphSpec(text = escapeXml("JARVIS AI PRESENTATION • $slideCount SLIDES"), fontSizePt = 12, bold = true, colorHex = accentHex)
                      )
                  )}
                </p:spTree>
              </p:cSld>
              <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
            </p:sld>
                """.trimIndent()
            )
        }
    }

    private fun buildContentSlideXml(
        deckTitle: String,
        slide: PresentationSlide,
        slideNumber: Int,
        totalSlides: Int,
        accentHex: String
    ): String {
        val bulletSpecs = slide.bullets.take(7).map { bullet ->
            ParagraphSpec(
                text = escapeXml("•  $bullet"),
                fontSizePt = if (slide.bullets.size > 5) 17 else 19,
                bold = false,
                colorHex = "1E293B",
                spaceAfterPt = 11
            )
        }
        val takeaway = slide.keyTakeaway?.trim()?.takeIf { it.isNotBlank() }
        val bodyHeight = if (takeaway != null) 3450000L else 4350000L

        val takeawayShapes = if (takeaway != null) {
            buildRectShape(
                id = 6,
                name = "TakeawayCard",
                x = 760000,
                y = 5180000,
                cx = 10672000,
                cy = 920000,
                fillHex = "E0F2FE"
            ) + "\n" +
            buildRectShape(
                id = 7,
                name = "TakeawayAccent",
                x = 760000,
                y = 5180000,
                cx = 90000,
                cy = 920000,
                fillHex = accentHex
            ) + "\n" +
            buildTextBox(
                id = 8,
                name = "TakeawayText",
                x = 960000,
                y = 5260000,
                cx = 10300000,
                cy = 760000,
                paragraphs = listOf(
                    ParagraphSpec(
                        text = escapeXml("Key Takeaway: $takeaway"),
                        fontSizePt = 15,
                        bold = true,
                        colorHex = "0F172A"
                    )
                )
            )
        } else {
            ""
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            append(
                """
              <p:cSld>
                <p:bg>
                  <p:bgPr>
                    <a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill>
                    <a:effectLst/>
                  </p:bgPr>
                </p:bg>
                <p:spTree>
                  <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                  <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
                  ${buildRectShape(id = 2, name = "HeaderBg", x = 0, y = 0, cx = SLIDE_WIDTH_EMU, cy = 1280000, fillHex = "0B1220")}
                  ${buildRectShape(id = 3, name = "HeaderStripe", x = 0, y = 1280000, cx = SLIDE_WIDTH_EMU, cy = 70000, fillHex = accentHex)}
                  ${buildTextBox(
                      id = 4,
                      name = "SlideTitle",
                      x = 760000,
                      y = 260000,
                      cx = 10672000,
                      cy = 820000,
                      paragraphs = listOf(
                          ParagraphSpec(text = escapeXml(slide.title), fontSizePt = 26, bold = true, colorHex = "FFFFFF")
                      )
                  )}
                  ${buildTextBox(
                      id = 5,
                      name = "SlideBullets",
                      x = 760000,
                      y = 1600000,
                      cx = 10672000,
                      cy = bodyHeight,
                      paragraphs = bulletSpecs
                  )}
                  $takeawayShapes
                  ${buildTextBox(
                      id = 9,
                      name = "SlideFooter",
                      x = 760000,
                      y = 6300000,
                      cx = 10672000,
                      cy = 350000,
                      paragraphs = listOf(
                          ParagraphSpec(
                              text = escapeXml("$deckTitle   •   Slide $slideNumber of $totalSlides"),
                              fontSizePt = 11,
                              bold = false,
                              colorHex = "64748B"
                          )
                      )
                  )}
                </p:spTree>
              </p:cSld>
              <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
            </p:sld>
                """.trimIndent()
            )
        }
    }

    private data class ParagraphSpec(
        val text: String,
        val fontSizePt: Int,
        val bold: Boolean,
        val colorHex: String,
        val spaceAfterPt: Int = 4
    )

    private fun buildRectShape(
        id: Int,
        name: String,
        x: Long,
        y: Long,
        cx: Long,
        cy: Long,
        fillHex: String
    ): String = """
        <p:sp>
          <p:nvSpPr><p:cNvPr id="$id" name="${escapeXml(name)}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
          <p:spPr>
            <a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
            <a:solidFill><a:srgbClr val="$fillHex"/></a:solidFill>
            <a:ln><a:noFill/></a:ln>
          </p:spPr>
        </p:sp>
    """.trimIndent()

    private fun buildTextBox(
        id: Int,
        name: String,
        x: Long,
        y: Long,
        cx: Long,
        cy: Long,
        paragraphs: List<ParagraphSpec>
    ): String {
        val paraXml = paragraphs.joinToString("\n") { p ->
            val sz = p.fontSizePt * 100
            val boldAttr = if (p.bold) "1" else "0"
            val spcAfter = p.spaceAfterPt * 100
            """
                <a:p>
                  <a:pPr><a:spcAft><a:spcPts val="$spcAfter"/></a:spcAft></a:pPr>
                  <a:r>
                    <a:rPr lang="en-US" sz="$sz" b="$boldAttr" dirty="0">
                      <a:solidFill><a:srgbClr val="${p.colorHex}"/></a:solidFill>
                    </a:rPr>
                    <a:t>${p.text}</a:t>
                  </a:r>
                  <a:endParaRPr lang="en-US" sz="$sz"/>
                </a:p>
            """.trimIndent()
        }
        return """
            <p:sp>
              <p:nvSpPr><p:cNvPr id="$id" name="${escapeXml(name)}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
              <p:spPr>
                <a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                <a:noFill/>
              </p:spPr>
              <p:txBody>
                <a:bodyPr wrap="square" rtlCol="0"/>
                <a:lstStyle/>
                $paraXml
              </p:txBody>
            </p:sp>
        """.trimIndent()
    }

    private fun normalizeHex(raw: String, fallback: String): String {
        val clean = raw.trim().removePrefix("#").uppercase()
        return if (clean.matches(Regex("^[0-9A-F]{6}$"))) clean else fallback
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\t', '\n', '\r' -> sb.append(ch)
                else -> if (ch.code >= 0x20) sb.append(ch)
            }
        }
        return sb.toString()
    }
}
