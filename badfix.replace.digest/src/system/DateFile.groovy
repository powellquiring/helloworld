package system

import java.util.regex.Matcher;

class DateFile {
    // cwa-20120404.log
    static Date fileToDate(File file) {
        Matcher parts = (file.name =~ /\D*(\d{8})\D*/)
        if ((parts.size() != 1) || parts[0].size() != 2) return null
        String numbers =  parts[0][1]
        Date date = Date.parse("yyyyMMdd", numbers)
        return date
    }

    static boolean tuesday(File file) {
        Date date = fileToDate(file)
        if (date == null) return false
        if (date.day == 2) return true
        return false
    }


}
