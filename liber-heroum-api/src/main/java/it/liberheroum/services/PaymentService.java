package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.payment.BasePaymentDTO;
import it.liberheroum.dto.payment.DetailPaymentDTO;
import it.liberheroum.dto.search.PaymentSearchRequest;
import it.liberheroum.model.Payment;
import it.liberheroum.security.JwtInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Database database;
    private final JwtInspector jwtInspector;

    public PagedResultDTO<BasePaymentDTO> findAllPayments(PaymentSearchRequest request) {
        ExpressionList<Payment> query = database.find(Payment.class)
                .setLabel("findAllPayments")
                .where().eq("appUser.keycloakId", jwtInspector.getSubject());
        request.applyFilter(query);
        request.applySortAndPagination(query, "paidAt");
        return PagedResultDTO.of(query.findPagedList(), BasePaymentDTO::from);
    }

    public DetailPaymentDTO getPaymentById(UUID id) {
        Payment payment = database.find(Payment.class)
                .setLabel("getPaymentById")
                .where().idEq(id).eq("appUser.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        return DetailPaymentDTO.from(payment);
    }
}
