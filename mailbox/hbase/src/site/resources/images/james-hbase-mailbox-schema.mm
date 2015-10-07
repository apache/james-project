<map version="0.9.0">
<!--To view this file, download free mind mapping software Freeplane from http://freeplane.sourceforge.net -->
<node TEXT="James HBase Mailbox Schema" ID="ID_1522661448" CREATED="1312975673167" MODIFIED="1312977622133" COLOR="#000000">
<font NAME="Liberation Sans" SIZE="20" BOLD="true"/>
<edge STYLE="linear" COLOR="#808080"/>
<hook NAME="MapStyle" max_node_width="600"/>
<hook NAME="accessories/plugins/HierarchicalIcons.properties"/>
<hook NAME="accessories/plugins/AutomaticLayout.properties"/>
<node TEXT="MESSAGES Table" POSITION="right" ID="ID_146373405" CREATED="1312975778142" MODIFIED="1312977557964" COLOR="#0033ff" STYLE="bubble" HGAP="61" VSHIFT="118">
<font NAME="SansSerif" SIZE="18"/>
<edge STYLE="sharp_bezier" WIDTH="8"/>
<attribute_layout NAME_WIDTH="46" VALUE_WIDTH="175"/>
<node TEXT="Row Key" ID="ID_966504852" CREATED="1312977332638" MODIFIED="1312977901259" COLOR="#00b439" HGAP="19" VSHIFT="6">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Row key is formed from mailbox ID, which is UUID folowed by message UID in reverse (Long.MAX_VALUE - uid)" ID="ID_1211104185" CREATED="1312977912485" MODIFIED="1313936488925" COLOR="#990000">
<font SIZE="14"/>
</node>
</node>
<node TEXT="Message MetaInformation Column Family" ID="ID_120928781" CREATED="1312977102333" MODIFIED="1312977207614" COLOR="#00b439">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Contains information, about the message : flags, properties, message size, etc. This information is stored in prefixed columns based on the information type. (e.g. system flags columns begin with &apos;sf:&apos;, properties begin with &apos;p:&apos;), etc." ID="ID_152445138" CREATED="1313936526013" MODIFIED="1313937393598" COLOR="#990000">
<font SIZE="14"/>
</node>
<node TEXT="Columns" ID="ID_588307567" CREATED="1313936865973" MODIFIED="1313936870872" COLOR="#990000">
<font SIZE="14"/>
<node TEXT="Meta columns are prefix with &apos;p:&apos; and record: body size, content size, date, lineCount (if text message), message modSequence, media type, and media sub type." ID="ID_1444672151" CREATED="1313936876285" MODIFIED="1313937027571" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="System flags columns: record the message system flags if present. Can be one of: sf:A, sf:DE, sf:DR, sf:F, sf:R, sf:S, sf:U. Value is the MARKER (currently X)." ID="ID_530864854" CREATED="1313937034469" MODIFIED="1313937378915" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="User flags columns: record message user flags if present. They begin with &apos;uf:&apos; prefix and are followed by the user flag name. Value is not important." ID="ID_1481299357" CREATED="1313937138884" MODIFIED="1313937361438" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Message properties columns: record message properties. They begin with &apos;p:&apos; prefix. The qualifier stores the properti namespace and local name separated by &apos;%%&apos; and the value as the column value." ID="ID_124342919" CREATED="1313937255421" MODIFIED="1313937353208" COLOR="#111111">
<font SIZE="12"/>
</node>
</node>
</node>
<node TEXT="Message Header Column Family" ID="ID_997617928" CREATED="1312977160645" MODIFIED="1312977239847" COLOR="#00b439">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Contains the message headers. This column family will merge with the message body column family." ID="ID_1039713363" CREATED="1313936757869" MODIFIED="1313936799283" COLOR="#990000">
<font SIZE="14"/>
</node>
<node TEXT="Columns" ID="ID_1702021503" CREATED="1313937404029" MODIFIED="1313937407488" COLOR="#990000">
<font SIZE="14"/>
<node TEXT="Data is stored in chunks and written/read by ChunkOutputStream and CunkInputStream classes. Column qualifiers are long values that start from 1." ID="ID_1495505366" CREATED="1313937411962" MODIFIED="1313937583513" COLOR="#111111">
<font SIZE="12"/>
</node>
</node>
</node>
<node TEXT="Message Body Column Family" ID="ID_1860200611" CREATED="1312977242189" MODIFIED="1312977252828" COLOR="#00b439">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Contains the message body. This column family will be merged with the headers column family." ID="ID_1200031498" CREATED="1313936802173" MODIFIED="1313936845530" COLOR="#990000">
<font SIZE="14"/>
</node>
<node TEXT="Columns" ID="ID_1369792562" CREATED="1313937404029" MODIFIED="1313937407488" COLOR="#990000">
<font SIZE="14"/>
<node TEXT="Data is stored in chunks and written/read by ChunkOutputStream and CunkInputStream classes. Column qualifiers are long values that start from 1." ID="ID_982985469" CREATED="1313937411962" MODIFIED="1313937587121" COLOR="#111111">
<font SIZE="12"/>
</node>
</node>
</node>
</node>
<node TEXT="MAILBOXES Table" POSITION="right" ID="ID_134161403" CREATED="1312975780902" MODIFIED="1312977003712" COLOR="#0033ff" HGAP="52" VSHIFT="-42">
<font NAME="SansSerif" SIZE="18"/>
<edge STYLE="sharp_bezier" WIDTH="8"/>
<node TEXT="Data Column Family" ID="ID_1623128385" CREATED="1312976367661" MODIFIED="1312977631456" COLOR="#00b439" HGAP="28" VSHIFT="2">
<font NAME="SansSerif" SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Columns" ID="ID_800289567" CREATED="1312977641181" MODIFIED="1312977676246" COLOR="#990000">
<font SIZE="14"/>
<node TEXT="Mailbox Name" ID="ID_846245417" CREATED="1312977678475" MODIFIED="1312977683828" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Mailbox User" ID="ID_1471784167" CREATED="1312977686803" MODIFIED="1312977694196" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Mailbox Namespace" ID="ID_71329885" CREATED="1312977707669" MODIFIED="1312977715693" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Mailbox Last Uid" ID="ID_1107277648" CREATED="1312977718997" MODIFIED="1312977728459" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Mailbox UIDValidity" ID="ID_197426098" CREATED="1312977733117" MODIFIED="1312977743239" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Mailbox Highest ModSeq" ID="ID_891220809" CREATED="1312977746739" MODIFIED="1312977754449" COLOR="#111111">
<font SIZE="12"/>
</node>
<node TEXT="Mailbox Message Count" ID="ID_682896631" CREATED="1312977757997" MODIFIED="1312978545892" COLOR="#111111">
<font NAME="SansSerif" SIZE="12" BOLD="false" ITALIC="false"/>
</node>
</node>
</node>
<node TEXT="Row Key" ID="ID_1967242452" CREATED="1312977644165" MODIFIED="1312977932107" COLOR="#00b439">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Current row key is formed from the mailbox &#xa;ID which is implemented with UUID" ID="ID_1704975170" CREATED="1312977787637" MODIFIED="1312977942914" COLOR="#990000" HGAP="25" VSHIFT="17">
<font SIZE="14"/>
</node>
</node>
</node>
<node TEXT="SUBSCRIPTIONS Table" POSITION="right" ID="ID_1342965000" CREATED="1312976873222" MODIFIED="1312977003719" HGAP="58" VSHIFT="-193" COLOR="#0033ff">
<font SIZE="18"/>
<edge STYLE="sharp_bezier" WIDTH="8"/>
<node TEXT="Data Column Family" ID="ID_1208761654" CREATED="1312977580421" MODIFIED="1312977589577" COLOR="#00b439">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="Columns" ID="ID_391778961" CREATED="1312978085189" MODIFIED="1312978189158" COLOR="#990000">
<font SIZE="14"/>
<node TEXT="Each existing column name represents &#xa;a user subscription to that particular mailbox." ID="ID_945378667" CREATED="1312978194157" MODIFIED="1312978307747" COLOR="#111111">
<font SIZE="12"/>
</node>
</node>
</node>
<node TEXT="Row Key" ID="ID_337360536" CREATED="1312977976917" MODIFIED="1312977980928" COLOR="#00b439">
<font SIZE="16"/>
<edge STYLE="bezier" WIDTH="thin"/>
<node TEXT="the user name (String)" ID="ID_979588600" CREATED="1312977982538" MODIFIED="1313937720101" COLOR="#990000" HGAP="22" VSHIFT="10">
<font SIZE="14"/>
</node>
</node>
</node>
</node>
</map>
