package ToolUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PermissionsUtil {
    private List<String> List_PermissionsKey = new ArrayList();
    private List<String> List_PermissionsName = new ArrayList();
    private List<String> List_PermissionsNotes = new ArrayList();

    public List<String> getList_PermissionsKey() {
        return List_PermissionsKey;
    }

    public List<String> getList_PermissionsName() {
        return List_PermissionsName;
    }

    public List<String> getList_PermissionsNotes() {
        return List_PermissionsNotes;
    }

    public PermissionsUtil() {
        try {
            InputStream data = PermissionsUtil.class.getResourceAsStream("/res/Permissionsinfo.dat");
            if (data == null) {
                File file = new File("src/res/Permissionsinfo.dat");
                if (!file.isFile()) {
                    return;
                }
                data = new FileInputStream(file);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("/", 3);
                    if (fields.length == 3) {
                        List_PermissionsKey.add(fields[0]);
                        List_PermissionsName.add(fields[1]);
                        List_PermissionsNotes.add(fields[2]);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
