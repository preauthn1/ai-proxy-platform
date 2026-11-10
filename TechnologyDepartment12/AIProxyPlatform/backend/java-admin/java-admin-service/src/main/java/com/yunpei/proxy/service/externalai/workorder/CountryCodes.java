package com.yunpei.proxy.service.externalai.workorder;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 投放国家码解析（对齐 Python countries.find_delivery_country_codes / parse_country_codes）。
 *
 * @author yunpei
 */
public final class CountryCodes {

    private CountryCodes() {}

    private static final Map<String, String> ALIASES = buildAliases();
    private static final List<Map.Entry<String, String>> NAME_ALIASES = buildNameAliases();
    private static final Pattern DELIVERY_COUNTRY_CONTEXT = Pattern.compile(
            "(?:投放(?:国家|地区)?|目标(?:国家|地区)|\\bcountry\\b|"
                    + "\\btarget\\s+(?:country|market)\\b|\\bmarket\\b)"
                    + "\\s*[:：]?\\s*(?:到|至|于)?\\s*"
                    + "(?!产品|素材|广告|链接|事件|时间|媒体|人群)([^\\n。；;]+)",
            Pattern.CASE_INSENSITIVE);

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        String[][] rows = {
            {"AD", "安道尔", "Andorra"},
            {"AE", "阿联酋", "United Arab Emirates"},
            {"AF", "阿富汗", "Afghanistan"},
            {"AG", "安提瓜和巴布达", "Antigua and Barbuda"},
            {"AI", "安圭拉", "Anguilla"},
            {"AL", "阿尔巴尼亚", "Albania"},
            {"AM", "亚美尼亚", "Armenia"},
            {"AO", "安哥拉", "Angola"},
            {"AR", "阿根廷", "Argentina"},
            {"AT", "奥地利", "Austria"},
            {"AU", "澳大利亚", "Australia"},
            {"AW", "阿鲁巴", "Aruba"},
            {"AZ", "阿塞拜疆", "Azerbaijan"},
            {"BA", "波黑", "Bosnia and Herzegovina"},
            {"BB", "巴巴多斯", "Barbados"},
            {"BD", "孟加拉国", "Bangladesh"},
            {"BE", "比利时", "Belgium"},
            {"BF", "布基纳法索", "Burkina Faso"},
            {"BG", "保加利亚", "Bulgaria"},
            {"BH", "巴林", "Bahrain"},
            {"BI", "布隆迪", "Burundi"},
            {"BJ", "贝宁", "Benin"},
            {"BM", "百慕大", "Bermuda"},
            {"BN", "文莱", "Brunei"},
            {"BO", "玻利维亚", "Bolivia"},
            {"BR", "巴西", "Brazil"},
            {"BS", "巴哈马", "Bahamas"},
            {"BT", "不丹", "Bhutan"},
            {"BW", "博茨瓦纳", "Botswana"},
            {"BY", "白俄罗斯", "Belarus"},
            {"BZ", "伯利兹", "Belize"},
            {"CA", "加拿大", "Canada"},
            {"CD", "刚果（金）", "Congo (DRC)"},
            {"CF", "中非", "Central African Republic"},
            {"CG", "刚果（布）", "Congo"},
            {"CH", "瑞士", "Switzerland"},
            {"CI", "科特迪瓦", "Cote d'Ivoire"},
            {"CL", "智利", "Chile"},
            {"CM", "喀麦隆", "Cameroon"},
            {"CN", "中国", "China"},
            {"CO", "哥伦比亚", "Colombia"},
            {"CR", "哥斯达黎加", "Costa Rica"},
            {"CU", "古巴", "Cuba"},
            {"CV", "佛得角", "Cabo Verde"},
            {"CY", "塞浦路斯", "Cyprus"},
            {"CZ", "捷克", "Czechia"},
            {"DE", "德国", "Germany"},
            {"DJ", "吉布提", "Djibouti"},
            {"DK", "丹麦", "Denmark"},
            {"DM", "多米尼克", "Dominica"},
            {"DO", "多米尼加", "Dominican Republic"},
            {"DZ", "阿尔及利亚", "Algeria"},
            {"EC", "厄瓜多尔", "Ecuador"},
            {"EE", "爱沙尼亚", "Estonia"},
            {"EG", "埃及", "Egypt"},
            {"ES", "西班牙", "Spain"},
            {"ET", "埃塞俄比亚", "Ethiopia"},
            {"FI", "芬兰", "Finland"},
            {"FJ", "斐济", "Fiji"},
            {"FR", "法国", "France"},
            {"GA", "加蓬", "Gabon"},
            {"GB", "英国", "United Kingdom"},
            {"GD", "格林纳达", "Grenada"},
            {"GE", "格鲁吉亚", "Georgia"},
            {"GF", "法属圭亚那", "French Guiana"},
            {"GH", "加纳", "Ghana"},
            {"GI", "直布罗陀", "Gibraltar"},
            {"GL", "格陵兰", "Greenland"},
            {"GM", "冈比亚", "Gambia"},
            {"GN", "几内亚", "Guinea"},
            {"GP", "瓜德罗普", "Guadeloupe"},
            {"GQ", "赤道几内亚", "Equatorial Guinea"},
            {"GR", "希腊", "Greece"},
            {"GT", "危地马拉", "Guatemala"},
            {"GU", "关岛", "Guam"},
            {"GW", "几内亚比绍", "Guinea-Bissau"},
            {"GY", "圭亚那", "Guyana"},
            {"HK", "中国香港", "Hong Kong"},
            {"HN", "洪都拉斯", "Honduras"},
            {"HR", "克罗地亚", "Croatia"},
            {"HT", "海地", "Haiti"},
            {"HU", "匈牙利", "Hungary"},
            {"ID", "印度尼西亚", "Indonesia"},
            {"IE", "爱尔兰", "Ireland"},
            {"IL", "以色列", "Israel"},
            {"IN", "印度", "India"},
            {"IQ", "伊拉克", "Iraq"},
            {"IR", "伊朗", "Iran"},
            {"IS", "冰岛", "Iceland"},
            {"IT", "意大利", "Italy"},
            {"JM", "牙买加", "Jamaica"},
            {"JO", "约旦", "Jordan"},
            {"JP", "日本", "Japan"},
            {"KE", "肯尼亚", "Kenya"},
            {"KG", "吉尔吉斯斯坦", "Kyrgyzstan"},
            {"KH", "柬埔寨", "Cambodia"},
            {"KM", "科摩罗", "Comoros"},
            {"KN", "圣基茨和尼维斯", "Saint Kitts and Nevis"},
            {"KR", "韩国", "South Korea"},
            {"KW", "科威特", "Kuwait"},
            {"KY", "开曼群岛", "Cayman Islands"},
            {"KZ", "哈萨克斯坦", "Kazakhstan"},
            {"LA", "老挝", "Laos"},
            {"LB", "黎巴嫩", "Lebanon"},
            {"LC", "圣卢西亚", "Saint Lucia"},
            {"LI", "列支敦士登", "Liechtenstein"},
            {"LK", "斯里兰卡", "Sri Lanka"},
            {"LR", "利比里亚", "Liberia"},
            {"LS", "莱索托", "Lesotho"},
            {"LT", "立陶宛", "Lithuania"},
            {"LU", "卢森堡", "Luxembourg"},
            {"LV", "拉脱维亚", "Latvia"},
            {"LY", "利比亚", "Libya"},
            {"MA", "摩洛哥", "Morocco"},
            {"MC", "摩纳哥", "Monaco"},
            {"MD", "摩尔多瓦", "Moldova"},
            {"ME", "黑山", "Montenegro"},
            {"MG", "马达加斯加", "Madagascar"},
            {"MK", "北马其顿", "North Macedonia"},
            {"ML", "马里", "Mali"},
            {"MM", "缅甸", "Myanmar"},
            {"MN", "蒙古", "Mongolia"},
            {"MO", "中国澳门", "Macao"},
            {"MQ", "马提尼克", "Martinique"},
            {"MR", "毛里塔尼亚", "Mauritania"},
            {"MT", "马耳他", "Malta"},
            {"MU", "毛里求斯", "Mauritius"},
            {"MV", "马尔代夫", "Maldives"},
            {"MW", "马拉维", "Malawi"},
            {"MX", "墨西哥", "Mexico"},
            {"MY", "马来西亚", "Malaysia"},
            {"MZ", "莫桑比克", "Mozambique"},
            {"NA", "纳米比亚", "Namibia"},
            {"NC", "新喀里多尼亚", "New Caledonia"},
            {"NE", "尼日尔", "Niger"},
            {"NG", "尼日利亚", "Nigeria"},
            {"NI", "尼加拉瓜", "Nicaragua"},
            {"NL", "荷兰", "Netherlands"},
            {"NO", "挪威", "Norway"},
            {"NP", "尼泊尔", "Nepal"},
            {"NZ", "新西兰", "New Zealand"},
            {"OM", "阿曼", "Oman"},
            {"PA", "巴拿马", "Panama"},
            {"PE", "秘鲁", "Peru"},
            {"PF", "法属波利尼西亚", "French Polynesia"},
            {"PG", "巴布亚新几内亚", "Papua New Guinea"},
            {"PH", "菲律宾", "Philippines"},
            {"PK", "巴基斯坦", "Pakistan"},
            {"PL", "波兰", "Poland"},
            {"PR", "波多黎各", "Puerto Rico"},
            {"PS", "巴勒斯坦", "Palestine"},
            {"PT", "葡萄牙", "Portugal"},
            {"PY", "巴拉圭", "Paraguay"},
            {"QA", "卡塔尔", "Qatar"},
            {"RE", "留尼汪", "Reunion"},
            {"RO", "罗马尼亚", "Romania"},
            {"RS", "塞尔维亚", "Serbia"},
            {"RU", "俄罗斯", "Russia"},
            {"RW", "卢旺达", "Rwanda"},
            {"SA", "沙特阿拉伯", "Saudi Arabia"},
            {"SC", "塞舌尔", "Seychelles"},
            {"SD", "苏丹", "Sudan"},
            {"SE", "瑞典", "Sweden"},
            {"SG", "新加坡", "Singapore"},
            {"SI", "斯洛文尼亚", "Slovenia"},
            {"SK", "斯洛伐克", "Slovakia"},
            {"SL", "塞拉利昂", "Sierra Leone"},
            {"SN", "塞内加尔", "Senegal"},
            {"SO", "索马里", "Somalia"},
            {"SR", "苏里南", "Suriname"},
            {"SV", "萨尔瓦多", "El Salvador"},
            {"SY", "叙利亚", "Syria"},
            {"SZ", "斯威士兰", "Eswatini"},
            {"TC", "特克斯和凯科斯群岛", "Turks and Caicos Islands"},
            {"TD", "乍得", "Chad"},
            {"TG", "多哥", "Togo"},
            {"TH", "泰国", "Thailand"},
            {"TJ", "塔吉克斯坦", "Tajikistan"},
            {"TL", "东帝汶", "Timor-Leste"},
            {"TM", "土库曼斯坦", "Turkmenistan"},
            {"TN", "突尼斯", "Tunisia"},
            {"TR", "土耳其", "Turkey"},
            {"TT", "特立尼达和多巴哥", "Trinidad and Tobago"},
            {"TW", "中国台湾", "Taiwan"},
            {"TZ", "坦桑尼亚", "Tanzania"},
            {"UA", "乌克兰", "Ukraine"},
            {"UG", "乌干达", "Uganda"},
            {"US", "美国", "United States"},
            {"UY", "乌拉圭", "Uruguay"},
            {"UZ", "乌兹别克斯坦", "Uzbekistan"},
            {"VE", "委内瑞拉", "Venezuela"},
            {"VG", "英属维尔京群岛", "British Virgin Islands"},
            {"VI", "美属维尔京群岛", "U.S. Virgin Islands"},
            {"VN", "越南", "Vietnam"},
            {"YE", "也门", "Yemen"},
            {"ZA", "南非", "South Africa"},
            {"ZM", "赞比亚", "Zambia"},
            {"ZW", "津巴布韦", "Zimbabwe"},
        };
        Map<String, String[]> extras = new LinkedHashMap<>();
        extras.put("AE", new String[] {"uae"});
        extras.put("AU", new String[] {"澳洲"});
        extras.put("BA", new String[] {"bosnia"});
        extras.put("BR", new String[] {"brasil"});
        extras.put("CD", new String[] {"democratic republic of the congo", "dr congo", "刚果民主共和国"});
        extras.put("CG", new String[] {"republic of the congo", "刚果共和国"});
        extras.put("CI", new String[] {"ivory coast"});
        extras.put("GB", new String[] {"uk", "britain", "great britain"});
        extras.put("HK", new String[] {"香港", "hong kong sar"});
        extras.put("ID", new String[] {"印尼"});
        extras.put("IN", new String[] {"भारत"});
        extras.put("KR", new String[] {"korea", "republic of korea", "南韩"});
        extras.put("MO", new String[] {"澳门", "macao sar", "macau"});
        extras.put("MX", new String[] {"méxico"});
        extras.put("PS", new String[] {"palestinian authority"});
        extras.put("SZ", new String[] {"swaziland"});
        extras.put("TH", new String[] {"ประเทศไทย"});
        extras.put("TR", new String[] {"turkiye", "türkiye"});
        extras.put("TW", new String[] {"台湾"});
        extras.put("US", new String[] {"america", "usa", "united states of america"});
        extras.put("VN", new String[] {"viet nam", "việt nam"});
        for (String[] row : rows) {
            String code = row[0];
            List<String> values = new ArrayList<>();
            values.add(code);
            values.add(row[1]);
            values.add(row[2]);
            if (extras.containsKey(code)) {
                for (String e : extras.get(code)) {
                    values.add(e);
                }
            }
            for (String value : values) {
                aliases.put(normalizeAlias(value), code);
            }
        }
        return Map.copyOf(aliases);
    }

    private static List<Map.Entry<String, String>> buildNameAliases() {
        List<Map.Entry<String, String>> list = new ArrayList<>();
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
            String alias = e.getKey();
            if (alias.length() == 2 && alias.chars().allMatch(c -> c < 128 && Character.isLetter(c))) {
                continue;
            }
            list.add(Map.entry(alias, e.getValue()));
        }
        list.sort((x, y) -> Integer.compare(y.getKey().length(), x.getKey().length()));
        return List.copyOf(list);
    }

    public static String normalizeCountryCode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> it) {
            for (Object item : it) {
                String code = normalizeCountryCode(item);
                if (code != null) {
                    return code;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("country_code", "code", "normalized_value", "value", "label", "name")) {
                String code = normalizeCountryCode(map.get(key));
                if (code != null) {
                    return code;
                }
            }
            return null;
        }
        return ALIASES.get(normalizeAlias(String.valueOf(value)));
    }

    public static List<String> parseCountryCodes(Object value) {
        if (value == null || "".equals(value)) {
            return List.of();
        }
        if (value instanceof Iterable<?> it) {
            List<String> codes = new ArrayList<>();
            for (Object item : it) {
                for (String code : parseCountryCodes(item)) {
                    if (!codes.contains(code)) {
                        codes.add(code);
                    }
                }
            }
            return codes;
        }
        if (value instanceof Map<?, ?>) {
            String code = normalizeCountryCode(value);
            return code == null ? List.of() : List.of(code);
        }
        String text = String.valueOf(value).strip();
        String direct = normalizeCountryCode(text);
        if (direct != null) {
            return List.of(direct);
        }
        List<String> codes = new ArrayList<>();
        for (String part : text.split("[,，、/+;；\\n]+")) {
            String code = normalizeCountryCode(part);
            if (code != null && !codes.contains(code)) {
                codes.add(code);
            }
        }
        if (!codes.isEmpty()) {
            return codes;
        }
        return findCountryMentions(text);
    }

    public static List<String> findCountryMentions(String text) {
        String normalized = normalizeAlias(text == null ? "" : text);
        record Hit(int start, int end, String code) {}
        List<Hit> matches = new ArrayList<>();
        for (Map.Entry<String, String> e : NAME_ALIASES) {
            String alias = e.getKey();
            String code = e.getValue();
            String escaped = Pattern.quote(alias);
            boolean ascii = alias.chars().allMatch(c -> c < 128);
            String pattern = ascii ? "(?<![a-z])" + escaped + "(?![a-z])" : escaped;
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalized);
            while (matcher.find()) {
                matches.add(new Hit(matcher.start(), matcher.end(), code));
            }
        }
        matches.sort((x, y) -> x.start != y.start
                ? Integer.compare(x.start, y.start)
                : Integer.compare(y.end - y.start, x.end - x.start));
        List<Hit> selected = new ArrayList<>();
        for (Hit hit : matches) {
            boolean overlap = false;
            for (Hit kept : selected) {
                if (hit.start < kept.end && hit.end > kept.start) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                selected.add(hit);
            }
        }
        selected.sort((x, y) -> Integer.compare(x.start, y.start));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (Hit hit : selected) {
            ordered.add(hit.code);
        }
        return new ArrayList<>(ordered);
    }

    public static List<String> findDeliveryCountryCodes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher matcher = DELIVERY_COUNTRY_CONTEXT.matcher(text);
        while (matcher.find()) {
            for (String code : parseCountryCodes(matcher.group(1))) {
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    private static String normalizeAlias(String value) {
        String n = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ");
    }
}
