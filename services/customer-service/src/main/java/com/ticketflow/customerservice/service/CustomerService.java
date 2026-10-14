package com.ticketflow.customerservice.service;

import com.ticketflow.customerservice.dto.CustomerRequest;
import com.ticketflow.customerservice.entities.Customer;
import com.ticketflow.customerservice.exceptions.CustomerNotFoundException;
import com.ticketflow.customerservice.exceptions.DuplicateEmailException;
import com.ticketflow.customerservice.repos.CustomerRepo;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepo repo;

    public CustomerService(CustomerRepo repo) {
        this.repo = repo;
    }

    public Customer create(CustomerRequest request) {
        String email = normalize(request.email());
        if (repo.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
        return repo.save(new Customer(
                request.firstName(), request.lastName(), email, request.phone()));
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return repo.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Customer> list() {
        return repo.findAll();
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer customer = get(id);
        String email = normalize(request.email());
        if (!customer.getEmail().equals(email) && repo.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
        customer.update(request.firstName(), request.lastName(), email, request.phone());
        return customer;
    }

    public void delete(Long id) {
        repo.delete(get(id));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
