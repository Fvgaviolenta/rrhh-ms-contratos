"""Genera la plantilla de ejemplo del contrato de trabajo (.docx) con campos $rrhh_* y $trabajador_*.

Uso: python3 -I scripts/generar_plantilla_ejemplo.py src/main/resources/plantillas/contrato-estandar.docx

Solo usa la biblioteca estandar (zipfile). El .docx resultante (6 partes, con estilos) es minimo pero valido: Word, LibreOffice
y Apache POI lo abren. Los campos se marcan con $ y el prefijo dice quien los llena: rrhh_ (SuperAdmin o
Admin de RRHH) o trabajador_ (el propio trabajador). $trabajador_nombre_completo se escribe partido en dos
runs a proposito, como lo deja Word a veces, para probar el reemplazo multi-run.
"""

import sys
import zipfile
from xml.sax.saxutils import escape

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

# Estilos minimos: el conversor a PDF (xdocreport) exige una parte de estilos.
STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Helvetica" w:hAnsi="Helvetica" w:cs="Helvetica"/><w:sz w:val="22"/><w:lang w:val="es-CL"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/><w:jc w:val="both"/></w:pPr></w:pPrDefault>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
</w:styles>"""


def run(texto, negrita=False):
    props = "<w:rPr><w:b/></w:rPr>" if negrita else ""
    return f'<w:r>{props}<w:t xml:space="preserve">{escape(texto)}</w:t></w:r>'


def parrafo(*runs, centrado=False, negrita=False):
    props = '<w:pPr><w:jc w:val="center"/></w:pPr>' if centrado else ""
    contenido = "".join(r if r.startswith("<w:r>") else run(r, negrita) for r in runs)
    return f"<w:p>{props}{contenido}</w:p>"


def celda(*parrafos):
    return '<w:tc><w:tcPr><w:tcW w:w="4500" w:type="dxa"/></w:tcPr>' + "".join(parrafos) + "</w:tc>"


def documento():
    cuerpo = [
        parrafo("CONTRATO INDIVIDUAL DE TRABAJO", centrado=True, negrita=True),
        parrafo(
            "En $rrhh_ciudad_firma, a la fecha de inicio indicada en este contrato, entre el empleador "
            "individualizado al pie de este documento, RUT $rrhh_empresa_rut, representado legalmente por "
            "$rrhh_representante_legal, ambos con domicilio en $rrhh_empresa_domicilio, en adelante "
            "\"el empleador\", y don o doña "
        ),
        # $trabajador_nombre_completo partido en dos runs; el segundo en negrita.
        "<w:p>"
        + run("Nombre del trabajador: ")
        + run("$trabajador_")
        + run("nombre_completo", negrita=True)
        + run(", de nacionalidad $trabajador_nacionalidad, nacido el $trabajador_fecha_nacimiento, "
              "de estado civil $trabajador_estado_civil, con domicilio en $trabajador_domicilio, "
              "en adelante \"el trabajador\", se ha convenido el siguiente contrato de trabajo:")
        + "</w:p>",
        parrafo(
            "PRIMERO. El trabajador se compromete a desempeñar el cargo de $rrhh_cargo, en "
            "$rrhh_lugar_trabajo, sin perjuicio de las labores similares que el empleador le encomiende."
        ),
        parrafo(
            "SEGUNDO. La jornada ordinaria de trabajo será de $rrhh_horas_semanales horas semanales, "
            "distribuidas de lunes a viernes según el reglamento interno de la empresa."
        ),
        parrafo(
            "TERCERO. El empleador pagará al trabajador un sueldo base mensual de $rrhh_sueldo_base pesos, "
            "que se liquidará y pagará por períodos vencidos, con los descuentos legales que correspondan."
        ),
        parrafo(
            "CUARTO. El presente contrato es de tipo $rrhh_tipo_contrato. Comienza a regir el "
            "$rrhh_fecha_inicio y su fecha de término es: $rrhh_fecha_termino."
        ),
        parrafo(
            "QUINTO. El presente contrato se firma en dos ejemplares, quedando uno en poder de cada parte. "
            "El trabajador declara haber recibido un ejemplar y conocer el reglamento interno."
        ),
        '<w:tbl><w:tblPr><w:tblW w:w="9000" w:type="dxa"/></w:tblPr>'
        '<w:tblGrid><w:gridCol w:w="4500"/><w:gridCol w:w="4500"/></w:tblGrid>'
        "<w:tr>"
        + celda(parrafo("______________________", centrado=True),
                parrafo("Empleador", centrado=True))
        + celda(parrafo("______________________", centrado=True),
                parrafo("Trabajador", centrado=True),
                parrafo("RUT $trabajador_rut", centrado=True))
        + "</w:tr></w:tbl>",
    ]
    seccion = (
        '<w:sectPr><w:footerReference w:type="default" r:id="rId1"/>'
        '<w:pgSz w:w="12240" w:h="15840"/>'
        '<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" '
        'w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>'
    )
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="{W_NS}" xmlns:r="{R_NS}"><w:body>'
        + "".join(cuerpo)
        + seccion
        + "</w:body></w:document>"
    )


def pie():
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:ftr xmlns:w="{W_NS}" xmlns:r="{R_NS}">'
        + parrafo("$rrhh_empresa_razon_social - Contrato individual de trabajo", centrado=True)
        + "</w:ftr>"
    )


def main():
    if len(sys.argv) != 2:
        sys.exit("uso: generar_plantilla_ejemplo.py <salida.docx>")
    with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", CONTENT_TYPES)
        z.writestr("_rels/.rels", ROOT_RELS)
        z.writestr("word/document.xml", documento())
        z.writestr("word/_rels/document.xml.rels", DOCUMENT_RELS)
        z.writestr("word/footer1.xml", pie())
        z.writestr("word/styles.xml", STYLES)


if __name__ == "__main__":
    main()
