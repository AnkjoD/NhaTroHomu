package vn.nhatrohomu.billing;

import java.util.List;
import vn.nhatrohomu.shared.Models.ServiceItem;

public interface ServiceCatalog {
  List<ServiceItem> items() throws Exception;

  void save(ServiceItem item) throws Exception;

  void archive(long id) throws Exception;

  List<ServiceItem> archivedItems() throws Exception;

  void deleteUnused(long id) throws Exception;

  int stopBilling(long id) throws Exception;
}
