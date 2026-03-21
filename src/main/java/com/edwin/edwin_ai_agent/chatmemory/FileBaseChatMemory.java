package com.edwin.edwin_ai_agent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileBaseChatMemory implements ChatMemory {
    private final String BASE_DIR;
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        //设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    //构造对象时，指定文件保存目录
    public FileBaseChatMemory(String dir){
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()){
            baseDir.mkdir();
        }
    }
    @Override
    public void add(String conversationId, Message messages) {
        //saveConversation(conversationId,List.of(messages));
        add(conversationId, List.of(messages));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> messagesList = getOrCreateConversation(conversationId);
        messagesList.addAll(messages);
        saveConversation(conversationId,messagesList);
    }

    @Override
    public List<Message> get(String conversationId) {  //注意旧版本会多一个lastN参数
        List<Message> messagesList = getOrCreateConversation(conversationId);
        return messagesList.stream()
                .skip(Math.max(0,messagesList.size() - 3))
                .toList();
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()){
            file.delete();
        }
    }

    //获取或创建会话消息的列表
    private List<Message> getOrCreateConversation(String conversationId){
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (Exception e) { // KryoException 也会进来
                // 读旧格式失败：删除坏/旧文件，避免一直卡死
                file.delete();
                messages = new ArrayList<>();
            }
        }
        return messages;
    }
    //保存会话消息
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok && !parent.exists()) {
                throw new IllegalStateException("Cannot create directory: " + parent.getAbsolutePath());
            }
        }
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //每个会话文件单独保存 （通用方法）
    private File getConversationFile(String conversationId){
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}