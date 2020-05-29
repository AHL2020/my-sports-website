package builder;

import beans.MatchBean;

public class MaintenanceManager {
    public static void main(String[] args) {
        int navSize = 5;
        for (int i = 0; i < 15; i++) {
            int startPage = test(i, navSize, 15);
            System.out.printf("c: %d, n: %d, s: %d \n", i, navSize, startPage);
        }
    }

    public static int test(int currentPage, int navSize, int lastPage) {
        int startPage = 0;
        if (currentPage >= (lastPage - navSize/2)) {
            startPage = lastPage - navSize;
        } else if (currentPage > navSize / 2) {
            startPage = currentPage - (navSize / 2);
        } else {
            startPage = 0;
        }
        for (int i = 0; i < navSize; i++) {
            int page = startPage + i;

            String fileName = "pageKey";
            if (page == 0) {
                fileName += ".ext";
            } else {
                fileName += "-" + page + ".ext";
            }
            if (page == currentPage) {
                fileName += "\t(active page)";
            }
            if (i == 0 && startPage > 0) {
                fileName += "\t(<<)";
            } else if (i == (navSize - 1) && page <= lastPage-navSize/2) {
                fileName += "\t(>>)";
            } else {
                fileName += "\t(" + (page + 1) + ")";
            }
            System.out.println(fileName);

        }
        return startPage;
    }
}
