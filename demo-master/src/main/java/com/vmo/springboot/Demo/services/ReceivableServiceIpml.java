package com.vmo.springboot.Demo.services;

import com.vmo.springboot.Demo.constant.EProcess;
import com.vmo.springboot.Demo.dto.Request.ReceivableRequestDto;
import com.vmo.springboot.Demo.dto.Respone.ReceivableResponseDto;
import com.vmo.springboot.Demo.model.*;
import com.vmo.springboot.Demo.repositories.IReceivableRepository;

import com.vmo.springboot.Demo.services.mail.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.transaction.Transactional;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@Component
public class ReceivableServiceIpml  {
    @Autowired
    IReceivableRepository iReceivableRepository;
    @Autowired
    ElectricBillServiceIpml electricBillService;

    @Autowired
    WaterBillServiceIpml waterBillService;

   @Autowired
   MailService emailService;

    @Autowired
    ServiceOtherServicesIpml serviceOtherService;

    public int separatePricing(int oldNumber, int newNumber, int unit) {
        int calculate = (newNumber - oldNumber) * unit;
        return calculate;
    }

    @Transactional
    public Receivable createReceivable(ReceivableRequestDto receivableRequestDto, Leases leases) {
        ElectricBill electricBill = electricBillService.getByNameAndStatus(receivableRequestDto.getName(), EProcess.PROCESSING.getId());
        if (electricBill == null) {
            electricBill = new ElectricBill().builder()
                    .name("elec_" + receivableRequestDto.getName())
                    .status(EProcess.PROCESSING.getId())
                    .build();
        }

        WaterBill waterBill = waterBillService.getByNameAndStatus(receivableRequestDto.getName(), EProcess.PROCESSING.getId());
        if (waterBill == null) {
            waterBill = new WaterBill().builder()
                    .name("water_" + receivableRequestDto.getName())
                    .status(EProcess.PROCESSING.getId())
                    .build();
        }
        int electricPayment = separatePricing(electricBill.getOldBillE(), electricBill.getNewBillE(), electricBill.getUnit());

        int waterPayment = separatePricing(waterBill.getOldBillW(), waterBill.getNewBillW(), waterBill.getUnit());

        Set<ServiceOther> serviceOthers = serviceOtherService.getServiceListById(
                receivableRequestDto.getServices()
        );
        //t??nh t???ng ti???n nh???ng service ???? ch???n
        int servicePayment = serviceOtherService.getTotalServicePriceIn(serviceOthers);

        //t??nh t???ng ti???n t???t c??? ph???i chi
        int calculationPayment = electricPayment + waterPayment + leases.getApartment().getPrice()
                + servicePayment;

        Receivable receivable = new Receivable().builder()
                .name(receivableRequestDto.getName())
                .payment(calculationPayment)
                .status(EProcess.PROCESSING.getId())
                .service(serviceOthers)
                .electricBill(electricBill)
                .waterBill(waterBill)
                .leases(leases)
                .build();

        iReceivableRepository.save(receivable);

        return receivable;
    }

    public int calculatePayment(ElectricBill electricBill, WaterBill waterBill, Receivable receivable, Leases leases) {
        int electricPayment = separatePricing(electricBill.getOldBillE(), electricBill.getNewBillE(), electricBill.getUnit());

        int waterPayment = separatePricing(waterBill.getOldBillW(), waterBill.getNewBillW(), waterBill.getUnit());

        //t??nh t???ng ti???n nh???ng service ???? ch???n
        int servicePayment = serviceOtherService.getTotalServicePriceIn(receivable.getService());

        //t??nh t???ng ti???n t???t c??? ph???i chi

        return electricPayment + waterPayment + leases.getApartment().getPrice()
                + servicePayment;
    }

    // T??nh ti???n c???a bill
    public String calculatePaymentForm(ElectricBill electricBill, WaterBill waterBill, Receivable receivable, Leases leases) {
        int electricPayment = separatePricing(electricBill.getOldBillE(),electricBill.getNewBillE() , electricBill.getUnit());

        int waterPayment = separatePricing(waterBill.getOldBillW(),waterBill.getNewBillW() , waterBill.getUnit());

        //t??nh t???ng ti???n nh???ng service ???? ch???n
        int servicePayment = serviceOtherService.getTotalServicePriceIn(receivable.getService());
        int apartmentPayment = leases.getApartment().getPrice();

        //t??nh t???ng ti???n
        int calculationPayment = electricPayment + waterPayment + apartmentPayment
                + servicePayment;

        return createBillForm(servicePayment, electricPayment, waterPayment, apartmentPayment, calculationPayment);
    }

    //t???o bill form
    public String createBillForm(int servicePayment, int electricPayment, int waterPayment, int calculationPayment, int apartmentPayment) {
        return "\n\tHo?? ????n\n"
                + "\nti???n nh??: " + apartmentPayment
                + "\nti???n ??i???n: " + electricPayment
                + "\nti???n n?????c: " + waterPayment
                + "\nti???n d???ch v???: " + servicePayment
                + "\nthanh to??n: " + calculationPayment;
    }


    public Receivable getByIdReceivable(int id) {
        if (iReceivableRepository.findById(id).isPresent()) {
            return iReceivableRepository.findById(id).get();
        }
        return null;
    }

//    public Receivable getByNameReceivable(String name) {
//        return iReceivableRepository.findByName(name);
//    }
//
//    public Receivable getByNameReceivableAndStatus(String name, int status) {
//        return iReceivableRepository.findByNameAndStatus(name, status);
//    }

    public ReceivableResponseDto getDetailById(int id) {
        Receivable receivable = getByIdReceivable(id);
        if (receivable == null) {
            return null;
        }

        String payment = calculatePaymentForm(receivable.getElectricBill(), receivable.getWaterBill(), receivable, receivable.getLeases());

        ReceivableResponseDto receivableResponseDto = new ReceivableResponseDto(
                receivable.getId(),
                receivable.getName(),
                receivable.getCreate_at(),
                receivable.getUpdate_at(),
                receivable.getStatus(),
                receivable.getService(),
                receivable.getElectricBill(),
                receivable.getWaterBill(),
                receivable.getLeases(),
                payment
        );

        return receivableResponseDto;
    }

    public Receivable updateReceivable(ReceivableRequestDto receivableRequestDto, Receivable receivable) {
        int payment = calculatePayment(receivable.getElectricBill(), receivable.getWaterBill(), receivable, receivable.getLeases());

        if (!receivable.getName().equalsIgnoreCase(receivableRequestDto.getName()) && receivable.getStatus() == EProcess.PROCESSING.getId()) {
            if (checkDuplicateNameAtProcessing(receivableRequestDto.getName(), EProcess.PROCESSING.getId()) == true) {
                return null;
            }
            receivable.setName(receivableRequestDto.getName());
            receivable.setCreate_at(receivable.getCreate_at());
            receivable.setUpdate_at(receivableRequestDto.getUpdateAt());
            receivable.setPayment(payment);
            iReceivableRepository.save(receivable);
        }

        receivable.setName(receivableRequestDto.getName());
        receivable.setPayment(payment);
        iReceivableRepository.save(receivable);

        return receivable;
    }

    public boolean checkDuplicateNameAtProcessing(String name, int status) {
        if (iReceivableRepository.findByNameAndStatus(name, status) != null) {
            return true;
        }
        return false;
    }

    public void disableReceivable(Receivable receivable) {
        receivable.setStatus(EProcess.DONE.getId());
        waterBillService.disableWaterBill(receivable.getWaterBill());
        electricBillService.disableElectricBill(receivable.getElectricBill());
        iReceivableRepository.save(receivable);
    }

    public List<Receivable> findAllReceivables() {
        return iReceivableRepository.findAll();
    }

    public List<Receivable> findAllReceivablesByName(String name) {
        return iReceivableRepository.findAllByName(name);
    }


    public String listServiceOtherOnAReservation(Receivable receivable) {
        String s = "";
        for (ServiceOther serviceOther : receivable.getService()) {
            s += serviceOther.toString();
        }
        return s;
    }


        public void sendMailToUser(Receivable receivable)  {
        String timeStamp = new SimpleDateFormat("yyyy.MM.DD").format(new Date());
        System.out.println("Test: " + timeStamp);
            emailService.sendSimpleEmail(

                    "hihi"+receivable.getLeases().getTenant().getEmail(),
                    timeStamp + "[#ROOM_" + receivable.getLeases().getApartment().getName().trim() + "] HO?? ????N C???N THANH TO??N ",
                    formatEmailReceivable(receivable)
            );
            System.out.println("Test: ");
//            } catch (MessagingException e) {
//                e.printStackTrace();
        }

    @Transactional
    public String formatEmailReceivable(Receivable receivable) {
        return
                "PH?? D???CH V??? C??N H??? " + receivable.getLeases().getApartment().getName() +
                        "\nT??n ch??? h???: " + receivable.getLeases().getTenant().getName() +
                        "\nN???i dung  thanh to??n: \n" +
                        "\n- Gi?? ph??ng: " + receivable.getLeases().getApartment().getPrice() + "VND" +
                        "\n- Gi?? ??i???n: " + receivable.getElectricBill().getUnit() + " (S??? c??: " + receivable.getElectricBill().getOldBillE() + "S??? m???i:" + receivable.getElectricBill().getOldBillE() + ")" +
                        "\n- Th??nh ti???n: " + separatePricing(receivable.getElectricBill().getOldBillE(), receivable.getElectricBill().getNewBillE(), receivable.getElectricBill().getUnit()) +
                        "\n- Gi?? n?????c s???ch: " + receivable.getWaterBill().getUnit() + " (S??? c??: " + receivable.getWaterBill().getOldBillW() + "S??? m???i:" + receivable.getWaterBill().getNewBillW() + ")" +
                        "\n- Th??nh ti???n: " + separatePricing(receivable.getWaterBill().getOldBillW(), receivable.getWaterBill().getNewBillW(), receivable.getWaterBill().getUnit()) +
                        "\n- C??c chi ph?? d???ch ph??? kh??c : " +
                        "\t" +
                        listServiceOtherOnAReservation(receivable) +
                        "\n=> T???NG C???NG: " + calculatePayment(
                        receivable.getElectricBill(), receivable.getWaterBill(), receivable, receivable.getLeases()
                ) + " VND"
                ;
    }


    @Scheduled(cron = "0 0 9 15 * ?") // 9h hang thang
 @Scheduled(fixedDelay = 1000)
    private void reminderPaid() {
        List<Receivable> receivables = iReceivableRepository.findAllByStatus(EProcess.PROCESSING.getId());
        for (Receivable receivable : receivables) {
            sendMailToUser(receivable);
        }
    }
}
