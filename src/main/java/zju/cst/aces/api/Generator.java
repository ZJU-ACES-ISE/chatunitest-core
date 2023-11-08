package zju.cst.aces.api;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.Message;

import java.util.List;

public interface Generator {

    public String generate(List<Message> messages);

}
