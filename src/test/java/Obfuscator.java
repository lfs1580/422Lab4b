import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.ZonedDateTime;

record BankRecords(Collection<Owner> owners, Collection<Account> accounts, Collection<RegisterEntry> registerEntries) { }

public class Obfuscator {
    private static Logger logger = LogManager.getLogger(Obfuscator.class.getName());

    public BankRecords obfuscate(BankRecords rawObjects) {
        // TODO: Obfuscate and return the records! Fill these in with your own
        // Example: mask SSN
        List<Owner> newOwners = new ArrayList<>();
	List<Account> newAccounts = new ArrayList<>();
	List<RegisterEntry> newRegisters = new ArrayList<>();
	try{
	MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Owner o : rawObjects.owners()) {
            String new_ssn = "***-**-" + o.ssn().substring(7);
            // other changes...
	    // hash name
	    byte[] hashBytes  = digest.digest(o.name().getBytes(StandardCharsets.UTF_8));
	    StringBuilder hexString = new StringBuilder();
	    for(byte b : hashBytes){
	    	hexString.append(String.format("%02x", b));
	    }
	    String hashedName = hexString.toString();
	    //apply variance to date
	    Random rand = new Random();

	    int daysVariance = rand.nextInt(11) - 5;
	    

	    LocalDate localDateStart = o.dob().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

	    LocalDate randDate = localDateStart.plusDays(daysVariance);
	    
	    LocalDateTime localDateTime =  randDate.atStartOfDay();
	    ZonedDateTime zonedDT = localDateTime.atZone(ZoneId.systemDefault());
	    Date newDate = Date.from(zonedDT.toInstant());



            newOwners.add(new Owner(hashedName, o.id(), newDate, new_ssn, o.address(), o.address2(), o.city(), o.state(), o.zip()));
        }

	//Obfuscate accounts
	int accountCount =  0;
	List<Account> accountList = new ArrayList<>(rawObjects.accounts());
	int accountsNum = accountList.size();

	for(Account a : accountList){
	    //hash name
	    byte[] hashBytes  = digest.digest(a.getName().getBytes(StandardCharsets.UTF_8)); 
	    StringBuilder hexString = new StringBuilder();
            for(byte b : hashBytes){
                hexString.append(String.format("%02x", b));
            }
 	    String hashedName = hexString.toString();
	    //shuffle balances
	    double newBalance = 0.0;
	    if(accountCount < accountsNum-1){
	        newBalance = accountList.get(accountCount+1).getBalance();
	    }else{
		newBalance = accountList.get(0).getBalance();
	    }
	    accountCount++;
	    //checks what type of account
	    Account newAccount;
	    if(a instanceof SavingsAccount sa){
	    	newAccount = new SavingsAccount(hashedName, sa.getId(), newBalance, sa.getInterestRate(), sa.getOwnerId());
	    }else if (a instanceof CheckingAccount ca){
	    	newAccount = new CheckingAccount(hashedName, ca.getId(), newBalance, 0, ca.getOwnerId());
	    }else{
	    	newAccount = null;
	    }

	    newAccounts.add(newAccount);

	}

	//obfuscate registers
	List<RegisterEntry> regList = new ArrayList<>(rawObjects.registerEntries());
	double startAmount = regList.get(0).amount();
	int entryIndex = 0;
	int entrySize = regList.size();
	for(RegisterEntry re : regList){
	    //obfuscate date
	    Random rand = new Random();
  
            int daysVariance = rand.nextInt(11) - 5;
            LocalDate localDateStart = re.date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  
            LocalDate randDate = localDateStart.plusDays(daysVariance);
  
            LocalDateTime localDateTime =  randDate.atStartOfDay();
	    ZonedDateTime zonedDT = localDateTime.atZone(ZoneId.systemDefault());
	    Date newDate = Date.from(zonedDT.toInstant());

	    double newAmount = 0.0;
	    //shuffles amount
	    if(entryIndex < entrySize-1){
	    	newAmount = regList.get(entryIndex+1).amount();
	    }else{
	    	newAmount = startAmount;
	    }
	    entryIndex++;
	    

	    //hash name
	    byte[] hashBytes  = digest.digest(re.entryName().getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for(byte b : hashBytes){
                    hexString.append(String.format("%02x", b));
                }
               String hashedName = hexString.toString();
	   newRegisters.add(new RegisterEntry(re.id(), re.accountId(), hashedName, re.amount(), newDate));
	}	


    	}catch(NoSuchAlgorithmException e){
	    e.printStackTrace();
	}

        Collection<Owner> obfuscatedOwners = newOwners;
        Collection<Account> obfuscatedAccounts = newAccounts;
        Collection<RegisterEntry> obfuscatedRegisterEntries = newRegisters;

        return new BankRecords(obfuscatedOwners, obfuscatedAccounts, obfuscatedRegisterEntries);
    }

    /**
     * Change the integration test suite to point to our obfuscated production
     * records.
     *
     * To use the original integration test suite files run
     *   "git checkout -- src/test/resources/persister_integ.properties"
     */
    public void updateIntegProperties() throws IOException {
        Properties props = new Properties();
        File propsFile = new File("src/test/resources/persister_integ.properties".replace('/', File.separatorChar));
        if (! propsFile.exists() || !propsFile.canWrite()) {
            throw new RuntimeException("Properties file must exist and be writable: " + propsFile);
        }
        try (InputStream propsStream = new FileInputStream(propsFile)) {
            props.load(propsStream);
        }
        props.setProperty("persisted.suffix", "_prod");
        logger.info("Updating properties file '{}'", propsFile);
        try (OutputStream propsStream = new FileOutputStream(propsFile)) {
            String comment = String.format(
                    "Note: Don't check in changes to this file!!\n" +
                    "#Modified by %s\n" +
                    "#to reset run 'git checkout -- %s'",
                    this.getClass().getName(), propsFile);
            props.store(propsStream, comment);
        }
    }

    public static void main(String[] args) throws Exception {
        // enable assertions
        Obfuscator.class.getClassLoader().setClassAssertionStatus("Obfuscator", true);
        logger.info("Loading Production Records");
        Persister.setPersisterPropertiesFile("persister_prod.properties");
        Bank bank = new Bank();
        bank.loadAllRecords();

        logger.info("Obfuscating records");
        Obfuscator obfuscator = new Obfuscator();
        // Make a copy of original values so we can compare length
        // deep-copy collections so changes in obfuscator don't impact originals
        BankRecords originalRecords = new BankRecords(
                new ArrayList<>(bank.getAllOwners()),
                new ArrayList<>(bank.getAllAccounts()),
                new ArrayList<>(bank.getAllRegisterEntries()));
        BankRecords obfuscatedRecords = obfuscator.obfuscate(originalRecords);

        logger.info("Saving obfuscated records");
        obfuscator.updateIntegProperties();
        Persister.resetPersistedFileNameAndDir();
        Persister.setPersisterPropertiesFile("persister_integ.properties");
        // old version of file is cached so we need to override prefix (b/c file changed
        // is not the one on classpath)
        Persister.setPersistedFileSuffix("_prod");
        // writeReords is cribbed from Bank.saveALlRecords(), refactor into common
        // method?
        Persister.writeRecordsToCsv(obfuscatedRecords.owners(), "owners");
        Map<Class<? extends Account>, List<Account>> splitAccounts = obfuscatedRecords
                .accounts()
                .stream()
                .collect(Collectors.groupingBy(rec -> rec.getClass()));
        Persister.writeRecordsToCsv(splitAccounts.get(SavingsAccount.class), "savings");
        Persister.writeRecordsToCsv(splitAccounts.get(CheckingAccount.class),"checking");
        Persister.writeRecordsToCsv(obfuscatedRecords.registerEntries(), "register");

        logger.info("Original   record counts: {} owners, {} accounts, {} registers",
                originalRecords.owners().size(),
                originalRecords.accounts().size(),
                originalRecords.registerEntries().size());
        logger.info("Obfuscated record counts: {} owners, {} accounts, {} registers",
                obfuscatedRecords.owners().size(),
                obfuscatedRecords.accounts().size(),
                obfuscatedRecords.registerEntries().size());

        if (obfuscatedRecords.owners().size() != originalRecords.owners().size())
            throw new AssertionError("Owners count mismatch");
        if (obfuscatedRecords.accounts().size() != originalRecords.accounts().size())
            throw new AssertionError("Account count mismatch");
        if (obfuscatedRecords.registerEntries().size() != originalRecords.registerEntries().size())
            throw new AssertionError("RegisterEntries count mismatch");
    }
}
