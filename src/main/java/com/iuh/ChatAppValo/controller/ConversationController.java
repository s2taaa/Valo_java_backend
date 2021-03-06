package com.iuh.ChatAppValo.controller;

import com.iuh.ChatAppValo.dto.ParticipantDTO;
import com.iuh.ChatAppValo.dto.request.ConversationCreateDTO;
import com.iuh.ChatAppValo.dto.response.ResponseConversation;
import com.iuh.ChatAppValo.dto.response.ResponseMessage;
import com.iuh.ChatAppValo.entity.*;
import com.iuh.ChatAppValo.entity.enumEntity.ConversationType;
import com.iuh.ChatAppValo.entity.enumEntity.MessageType;
import com.iuh.ChatAppValo.jwt.response.MessageResponse;
import com.iuh.ChatAppValo.repositories.*;
import com.iuh.ChatAppValo.services.AmazonS3Service;
import com.iuh.ChatAppValo.services.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/conversations")
public class ConversationController {
    @Autowired
    private AmazonS3Service s3Service;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ReadTrackingRepository readTrackingRepository;

    @Autowired
    private FriendRepository friendRepository;

    private static final Logger logger = Logger.getLogger(ConversationController.class.getName());

//    /**
//     * l???y danh s??ch h???i tho???i
//     * @param account
//     * @param pageable
//     * @return
//     */
//    @GetMapping
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<?> getConversationsOfUser(@AuthenticationPrincipal Account account, Pageable pageable){
//        User user = userRepository.findDistinctByPhone(account.getUsername())
//                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
//        Page<Conversation> conversationPage = conversationRepository.getConversationsOfUser(user.getId(), pageable);
//        if (conversationPage == null)
//            return ResponseEntity.badRequest().body(new MessageResponse("conversation null"));
//        return ResponseEntity.ok(conversationPage);
//    }

    /**
     * l???y danh s??ch h???i tho???i v???i tin nh???n m???i nh???t v?? s??? tin nh???n m???i
     * @param account
     * @param pageable
     * @return responseConversationPage - Page ResponseConversation gi???m d???n theo ng??y c?? tin nh???n m???i
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getConversationsOfUser(@AuthenticationPrincipal Account account, Pageable pageable){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        Page<Conversation> conversationPage = conversationRepository.getConversationsOfUser(user.getId(), pageable);
        if (conversationPage == null)
            return ResponseEntity.badRequest().body(new MessageResponse("conversation null"));
        List<Conversation> conversationList = conversationPage.getContent();
        //T???o responseConversationList
        List<ResponseConversation> responseConversationList = new ArrayList<ResponseConversation>();
        for (Conversation conversation : conversationList) {
            if (isParticipant(conversation, user)){
                Message lastMessage = messageRepository.getLastMessageOfConversation(conversation.getId()).get();
                ReadTracking readTracking = readTrackingRepository.findByConversationIdAndUserId(conversation.getId(), user.getId());
                if(lastMessage.getMessageType() !=MessageType.SYSTEM){
                    User messageSender = userRepository.findById(lastMessage.getSenderId()).get();
                    ResponseMessage responseMessage = ResponseMessage.builder()
                            .message(lastMessage)
                            .userImgUrl(messageSender.getImgUrl())
                            .userName(messageSender.getName())
                            .build();
                    ResponseConversation responseConversation = ResponseConversation.builder()
                            .conversation(conversation)
                            .lastMessage(responseMessage)
                            .unReadMessage(readTracking.getUnReadMessage())
                            .build();
                    responseConversationList.add(responseConversation);
                }else {
                    ResponseMessage responseMessage = ResponseMessage.builder()
                            .message(lastMessage)
                            .userImgUrl(null)
                            .userName(null)
                            .build();
                    ResponseConversation responseConversation = ResponseConversation.builder()
                            .conversation(conversation)
                            .lastMessage(responseMessage)
                            .unReadMessage(readTracking.getUnReadMessage())
                            .build();
                    responseConversationList.add(responseConversation);
                }

            }
        }
        //S???p x???p l???i responseConversationList ????? tr??? v??? danh s??ch conversation theo ng??y c?? tin nh???n m???i gi???m d???n
        Collections.sort(responseConversationList, new Comparator<ResponseConversation>() {
            @Override
            public int compare(ResponseConversation o1, ResponseConversation o2) {
                return o2.getLastMessage().getMessage().getSendAt().compareTo(o1.getLastMessage().getMessage().getSendAt());
            }
        });
        //T???o Page
        Page<ResponseConversation> responseConversationPage = new PageImpl<>(responseConversationList, conversationPage.getPageable(), conversationPage.getTotalElements());
        return ResponseEntity.ok(responseConversationPage);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createGroup(@AuthenticationPrincipal Account account, @RequestBody ConversationCreateDTO conversation){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));

        // T???o danh s??ch participants
        Set<Participant> participants = new HashSet<>();
        for (Participant participant : conversation.getParticipants()) {
            User userToAdd = userRepository.findById(participant.getUserId()).get();
            //ki???m tra ng?????i d??ng ???????c th??m c?? t???n t???i hay kh??ng
            if (userToAdd == null)
                logger.log(Level.INFO, "userId = {} kh??ng t???n t???i", userToAdd.getName());
            Participant newParticipant = Participant.builder()
                    .isAdmin(false)
                    .userId(participant.getUserId())
                    .addByUserId(user.getId())
                    .addTime(new Date())
                    .build();
            participants.add(newParticipant);
        }

        // ki???m tra danh s??ch r???ng hay kh??ng
        if (participants.isEmpty())
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Ch??a c?? th??nh vi??n cho nh??m, vui l??ng ch???n th??nh vi??n ???????c th??m v??o"));

        // th??m ng?????i t???o nh??m v??o danh s??ch th??nh vi??n
        Participant newParticipant = Participant.builder()
                .isAdmin(true)
                .userId(user.getId())
                .addByUserId(null)
                .addTime(new Date())
                .build();
        participants.add(newParticipant);

        // T???o group
        Conversation newConversation = Conversation.builder()
                .name(conversation.getName())
                .conversationType(ConversationType.GROUP)
                .createdByUserId(user.getId())
                .participants(participants)
                .imageUrl("https://chatappvalo.s3.ap-southeast-1.amazonaws.com/5809830.png")
                .build();
        conversationRepository.save(newConversation);

        System.out.println(newConversation);

        // g???i tin nh???n h??? th???ng
        for (Participant participant: participants) {
            User userToAdd = userRepository.findById(participant.getUserId()).get();
            if (!userToAdd.getId().equals(user.getId())){
                String messageContent = user.getName() + " ???? th??m " + userToAdd.getName() + " v??o nh??m";
                sendSystemMessage(newConversation, messageContent);
            }

        }

        return ResponseEntity.ok(newConversation);
    }

    /**
     * Xu???t danh s??ch b???n ????? th??m v??o conversation
     * @param account
     * @param pageable
     * @return
     */
    @GetMapping("/add/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFriendListToAdd(@AuthenticationPrincipal Account account,
                                                @PathVariable("conversationId") String conversationId, Pageable pageable){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NullPointerException("H???i tho???i kh??ng t???n t???i"));
        Page<Friend> friendPage = friendRepository.getFriendListOfUserWithUserId(user.getId(), pageable);
        List<Friend> friendList = friendPage.getContent();
        System.out.println(friendList);
        if (friendList.isEmpty())
            return ResponseEntity.ok(new ArrayList<>());
        List<Friend> responseList = new ArrayList<>();
        for (Friend friend :friendList) {
            User userToCheck = userRepository.findById(friend.getFriendId())
                    .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
            if (!isParticipant(conversation, userToCheck)){
                responseList.add(friend);
            }
        }
        if (responseList.isEmpty())
            logger.log(Level.INFO, "No more friend to add in this conversation");
        return ResponseEntity.ok(new PageImpl<Friend>(responseList, pageable, responseList.size()));
    }

    /**
     * Th??m th??nh vi??n v??o nh??m
     * @param account
     * @param participantDTO
     * @return
     */
    @PutMapping("/add")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addParticipant(@AuthenticationPrincipal Account account, @RequestBody ParticipantDTO participantDTO, Pageable pageable){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        User userToAdd = userRepository.findById(participantDTO.getUserId())
                .orElseThrow(() -> new NullPointerException("Ng?????i d??ng ???????c th??m kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(participantDTO.getConversationId())
                .orElseThrow(() -> new NullPointerException("H???i tho???i kh??ng t???n t???i"));
        // ki???m tra c?? ph???i h???i tho???i c?? nh??n hay kh??ng?
        if (conversation.getConversationType() == ConversationType.ONE_ONE){
            return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? th??m th??nh vi??n v??o h???i tho???i c?? nh??n"));
        }
        // ki???m tra ng?????i ???????c th??m c?? ph???i th??nh vi??n c???a nh??m hay kh??ng?
        if (isParticipant(conversation, userToAdd)){
            return ResponseEntity.badRequest().body(new MessageResponse(userToAdd.getName() + " ???? l?? th??nh vi??n c???a nh??m"));
        }
        Set<Participant> participantSet = conversation.getParticipants();
        // ki???m tra ng?????i d??ng c?? ph???i th??nh vi??n c???a nh??m hay kh??ng?
        if (isParticipant(conversation, user)){
            // t???o participant cho userToAdd
            Participant newParticipant = Participant.builder()
                    .addByUserId(user.getId())
                    .addTime(new Date())
                    .isAdmin(false)
                    .userId(userToAdd.getId())
                    .build();
            participantSet.add(newParticipant);
            conversation.setParticipants(participantSet);
            conversationRepository.save(conversation);
            // g???i tin nh???n h??? th???ng th??ng b??o th??nh vi??n m???i ???????c th??m
            String messageContent = user.getName() + " ???? th??m " + userToAdd.getName() + " v??o nh??m";
            sendSystemMessage(conversation, messageContent);
            List<Participant> listParticipant = new ArrayList<Participant>(participantSet);
            return ResponseEntity.ok(new PageImpl<Participant>(listParticipant,pageable,participantSet.size()));
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? th??m khi kh??ng ph???i th??nh vi??n c???a nh??m"));
    }

    /**
     * R???i nh??m
     * @param account
     * @param conversationId
     * @return
     */
    @PutMapping("/leave/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> leaveConversation(@AuthenticationPrincipal Account account
            , @PathVariable("conversationId") String conversationId){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NullPointerException("H???i tho???i kh??ng t???n t???i"));
        // ki???m tra c?? ph???i h???i tho???i c?? nh??n hay kh??ng?
        if (conversation.getConversationType() == ConversationType.ONE_ONE){
            return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? r???i nh??m trong h???i tho???i c?? nh??n"));
        }
        Set<Participant> participantSet = conversation.getParticipants();
        if(participantSet.isEmpty())
            return ResponseEntity.ok(new ArrayList<>());
        // ki???m tra ng?????i d??ng c?? ph???i th??nh vi??n c???a nh??m hay kh??ng?
        if (isParticipant(conversation, user)){
            // t???o participant cho userToAdd
            Participant leaveParticipant = Participant.builder()
                    .userId(user.getId())
                    .build();

            participantSet.removeIf(participant -> leaveParticipant.getUserId().equals(participant.getUserId()));
            conversation.setParticipants(participantSet);
            conversationRepository.save(conversation);
            // g???i tin nh???n h??? th???ng th??ng b??o th??nh vi??n m???i ???????c th??m
            String messageContent = user.getName() + " ???? r???i kh???i nh??m";
            sendSystemMessage(conversation, messageContent);
            return ResponseEntity.ok(new MessageResponse(messageContent));
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? r???i nh??m khi kh??ng ph???i th??nh vi??n c???a nh??m"));
    }

    /**
     * ??u???i th??nh vi??n v??o nh??m
     * @param account
     * @param participantDTO
     * @return
     */
    @PutMapping("/kick")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> kickParticipant(@AuthenticationPrincipal Account account, @RequestBody ParticipantDTO participantDTO, Pageable pageable) {
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        User userToKick = userRepository.findById(participantDTO.getUserId())
                .orElseThrow(() -> new NullPointerException("Ng?????i d??ng ???????c kick kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(participantDTO.getConversationId())
                .orElseThrow(() -> new NullPointerException("H???i tho???i kh??ng t???n t???i"));
        // ki???m tra c?? ph???i h???i tho???i c?? nh??n hay kh??ng?
        if (conversation.getConversationType() == ConversationType.ONE_ONE) {
            return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? kick th??nh vi??n v??o h???i tho???i c?? nh??n"));
        }
        Set<Participant> participantSet = conversation.getParticipants();
        // ki???m tra ng?????i d??ng v?? ng?????i b??? kick c?? ph???i th??nh vi??n c???a nh??m hay kh??ng?
        if (isParticipant(conversation, user) && isParticipant(conversation, userToKick)){
            // ki???m tra quy???n admin c???a ng?????i d??ng, v?? kh??ng th??? kick admin
            if (isAdmin(conversation, user) && !isAdmin(conversation, userToKick)){
                for (Participant participant: participantSet) {
                    if (userToKick.getId().equals(participant.getUserId())){
                        participantSet.remove(participant);
                        conversation.setParticipants(participantSet);
                        conversationRepository.save(conversation);
                        // g???i tin nh???n h??? th???ng th??ng b??o th??nh vi??n m???i ???????c th??m
                        String messageContent = user.getName() + " ???? ??u???i " + userToKick.getName() + " ra kh???i nh??m";
                        sendSystemMessage(conversation, messageContent);
                        List<Participant> listParticipant = new ArrayList<Participant>(participantSet);
                        return ResponseEntity.ok(new PageImpl<Participant>(listParticipant,pageable,participantSet.size()));
                    }
                }
            }
            return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? kick th??nh vi??n khi kh??ng ph???i admin hay ng?????i b??? kick l?? admin"));
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? kick khi kh??ng ph???i th??nh vi??n c???a nh??m"));
    }

    /**
     * X??a nh??m
     * @param account
     * @param conversationId
     * @return
     */
    @DeleteMapping("/delete/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteConversation(@AuthenticationPrincipal Account account
            , @PathVariable("conversationId") String conversationId){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NullPointerException("H???i tho???i kh??ng t???n t???i"));
        // ki???m tra c?? ph???i h???i tho???i c?? nh??n hay kh??ng?
        if (conversation.getConversationType() == ConversationType.ONE_ONE){
            return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? x??a h???i tho???i c?? nh??n"));
        }
        if (user.getId().equals(conversation.getCreatedByUserId())){
            List<Message> messages = messageRepository.getMessageOfConversation(conversationId, Pageable.unpaged()).getContent();
            messageRepository.deleteAll(messages);
            conversationRepository.delete(conversation);
            return ResponseEntity.ok(new MessageResponse("X??a nh??m th??nh c??ng"));
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Kh??ng th??? x??a nh??m khi ng?????i t???o nh??m c???a nh??m"));
    }

    /**
     * ?????i ???nh ?????i di???n nh??m
     * @param account
     * @param conversationId
     * @param multipartFile
     * @return
     */
    @PutMapping("/{conversationId}/changeImage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changeImage(@AuthenticationPrincipal Account account
            , @PathVariable("conversationId") String conversationId, MultipartFile multipartFile){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(conversationId).get();
        if (conversation == null)
            return ResponseEntity.badRequest().body(new MessageResponse("Conversation null"));
        logger.log(Level.INFO, "user = {} is changing conversation image", user.getName());
        if (multipartFile.isEmpty())
            return ResponseEntity.badRequest().body(new MessageResponse("No files is selected"));
        String newImageUrl = s3Service.uploadFile(multipartFile);
        conversation.setImageUrl(newImageUrl);
        conversationRepository.save(conversation);
        String messageContent = user.getName() + " ???? ?????i ???nh ?????i di???n nh??m";
        sendSystemMessage(conversation, messageContent);
        return ResponseEntity.ok(conversation);
    }

    @PutMapping("/rename/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> renameConversation(@AuthenticationPrincipal Account account
            , @RequestParam("newName") String newName, @PathVariable("conversationId") String conversationId){
        User user = userRepository.findDistinctByPhone(account.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Ng?????i d??ng kh??ng t???n t???i"));
        Conversation conversation = conversationRepository.findById(conversationId).get();
        if (conversation == null)
            return ResponseEntity.badRequest().body(new MessageResponse("Conversation null"));
        logger.log(Level.INFO, "user = {} is changing conversation name", user.getName());
        if (newName.equals(""))
            return ResponseEntity.badRequest().body(new MessageResponse("Nh???p t??n m???i cho nh??m ????? ?????i t??n nh??m"));
        conversation.setName(newName);
        conversationRepository.save(conversation);
        String messageContent = user.getName() + " ???? ?????i t??n nh??m th??nh "+ newName;
        sendSystemMessage(conversation, messageContent);
        return ResponseEntity.ok(conversation);
    }

    private void sendSystemMessage(Conversation conversation, String messageBody){
        Message message = Message.builder()
                .senderId(null) // system message => senderId null
                .conversationId(conversation.getId())
                .messageType(MessageType.SYSTEM)
                .content(messageBody)
                .pin(false)
                .build();
        chatService.sendSystemMessage(message, conversation);
    }

    private boolean isParticipant(Conversation conversation, User userToCheck){
        Set<Participant> participantSet = conversation.getParticipants();
        // ki???m tra ng?????i d??ng c?? ph???i th??nh vi??n c???a nh??m hay kh??ng?
        for (Participant participant: participantSet) {
            if (userToCheck.getId().equals(participant.getUserId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin(Conversation conversation, User userToCheck){
        Set<Participant> participantSet = conversation.getParticipants();
        // ki???m tra ng?????i d??ng c?? ph???i th??nh vi??n c???a nh??m hay kh??ng?
        for (Participant participant: participantSet) {
            if (userToCheck.getId().equals(participant.getUserId())) {
                // ki???m tra ng?????i d??ng c?? ph???i admin c???a nh??m hay kh??ng?
                if (participant.isAdmin())
                    return true;
            }
        }
        return false;
    }
}
